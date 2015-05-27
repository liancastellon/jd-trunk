package org.jdownloader.gui.views.components.packagetable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.Icon;
import javax.swing.ListSelectionModel;

import jd.controlling.packagecontroller.AbstractNode;
import jd.controlling.packagecontroller.AbstractNodeVisitor;
import jd.controlling.packagecontroller.AbstractPackageChildrenNode;
import jd.controlling.packagecontroller.AbstractPackageNode;
import jd.controlling.packagecontroller.ChildrenView;
import jd.controlling.packagecontroller.PackageController;
import jd.controlling.packagecontroller.PackageControllerComparator;

import org.appwork.scheduler.DelayedRunnable;
import org.appwork.storage.Storage;
import org.appwork.storage.config.ValidationException;
import org.appwork.storage.config.events.GenericConfigEventListener;
import org.appwork.storage.config.handler.KeyHandler;
import org.appwork.swing.exttable.ExtColumn;
import org.appwork.swing.exttable.ExtDefaultRowSorter;
import org.appwork.swing.exttable.ExtTableModel;
import org.appwork.utils.swing.EDTRunner;
import org.jdownloader.gui.views.SelectionInfo;
import org.jdownloader.gui.views.SelectionInfo.PackageView;
import org.jdownloader.gui.views.components.packagetable.PackageControllerTableModelData.PackageControllerTableModelDataPackage;
import org.jdownloader.logging.LogController;
import org.jdownloader.settings.staticreferences.CFG_GUI;

public abstract class PackageControllerTableModel<PackageType extends AbstractPackageNode<ChildrenType, PackageType>, ChildrenType extends AbstractPackageChildrenNode<PackageType>> extends ExtTableModel<AbstractNode> {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public static enum TOGGLEMODE {
        CURRENT,
        TOP,
        BOTTOM
    }

    @Override
    protected int findFirstSelectedRow(ListSelectionModel s) {
        try {
            final SelectionInfo<PackageType, ChildrenType> selectionInfo = getTable().getSelectionInfo(true, true);
            final PackageView<PackageType, ChildrenType> firstPackageView = selectionInfo.getPackageViews().size() == 0 ? null : selectionInfo.getPackageViews().get(0);
            if (firstPackageView != null) {
                final PackageType pkg = firstPackageView.getPackage();
                if (pkg.getControlledBy() == null) {
                    return getRowforObject(pkg);
                } else {
                    final boolean full;
                    final boolean readL = pkg.getModifyLock().readLock();
                    try {
                        full = pkg.getChildren().size() == 0 || pkg.getChildren().size() == firstPackageView.getChildren().size();
                    } finally {
                        pkg.getModifyLock().readUnlock(readL);
                    }
                    if (full) {
                        return getRowforObject(pkg);
                    }
                }
            }
            final ChildrenType firstLink = selectionInfo.getChildren().size() == 0 ? null : selectionInfo.getChildren().get(0);
            if (firstLink != null) {
                return getRowforObject(firstLink);
            }
            return -1;
        } catch (Throwable e) {
            // catch ... exceptions here would break the whole table
            e.printStackTrace();
        }
        return -1;

    }

    public abstract class TableDataModification {
        protected abstract void modifyTableData(List<PackageType> packages);

        protected abstract void modifyPackageData(PackageType pkg, List<ChildrenType> unfilteredChildren);

        protected PackageControllerTableModelCustomizer finalizeTableModification() {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public PackageControllerTable<PackageType, ChildrenType> getTable() {
        return (PackageControllerTable<PackageType, ChildrenType>) super.getTable();
    }

    private boolean tristateSorterEnabled = true;

    public boolean isTristateSorterEnabled() {
        return tristateSorterEnabled;
    }

    public void setTristateSorterEnabled(boolean tristateSorterEnabled) {
        this.tristateSorterEnabled = tristateSorterEnabled;
    }

    private static final String                                          SORT_ORIGINAL            = "ORIGINAL";

    private final DelayedRunnable                                        asyncRefresh;
    protected final PackageController<PackageType, ChildrenType>         pc;
    private final DelayedRunnable                                        asyncRecreate;

    private CopyOnWriteArrayList<TableDataModification>                  tableModifiers           = new CopyOnWriteArrayList<TableDataModification>();
    protected PackageControllerTableModelData<PackageType, ChildrenType> tableData                = new PackageControllerTableModelData<PackageType, ChildrenType>();

    private ScheduledExecutorService                                     queue                    = DelayedRunnable.getNewScheduledExecutorService();

    private final DelayedRunnable                                        asyncRecreateFast;

    private final Storage                                                storage;

    private final AtomicLong                                             repaintFiredCounter      = new AtomicLong(0);
    private final AtomicLong                                             structureChangedCounter  = new AtomicLong(0);
    private final AtomicLong                                             repaintRequested         = new AtomicLong(0);
    private final AtomicLong                                             structureChangeRequested = new AtomicLong(0);
    private final AtomicLong                                             structureChangeProcessed = new AtomicLong(0);

    private volatile boolean                                             hideSinglePackage        = false;

    public boolean isHideSinglePackage() {
        return hideSinglePackage;
    }

    public PackageControllerTableModel(final PackageController<PackageType, ChildrenType> pc, String id) {
        super(id);
        storage = getStorage();
        resetSorting();
        CFG_GUI.HIDE_SINGLE_CHILD_PACKAGES.getEventSender().addListener(new GenericConfigEventListener<Boolean>() {

            @Override
            public void onConfigValueModified(KeyHandler<Boolean> keyHandler, Boolean newValue) {
                hideSinglePackage = Boolean.TRUE.equals(newValue);
            }

            @Override
            public void onConfigValidatorError(KeyHandler<Boolean> keyHandler, Boolean invalidValue, ValidationException validateException) {
            }
        }, false);
        hideSinglePackage = CFG_GUI.HIDE_SINGLE_CHILD_PACKAGES.isEnabled();
        this.pc = pc;
        asyncRefresh = new DelayedRunnable(queue, 150l, 500l) {

            @Override
            public String getID() {
                return "PackageControllerTableModel_Refresh" + pc.getClass().getName();
            }

            @Override
            public void delayedrun() {
                fireRepaint();
            }
        };
        asyncRecreate = new DelayedRunnable(queue, 50l, 1000l) {

            @Override
            public String getID() {
                return "PackageControllerTableModel_Recreate" + pc.getClass().getName();
            }

            @Override
            public void delayedrun() {
                fireStructureChange();
            }
        };

        asyncRecreateFast = new DelayedRunnable(queue, 10l, 50l) {

            @Override
            public String getID() {
                return "PackageControllerTableModel_RecreateFast" + pc.getClass().getName();
            }

            @Override
            public void delayedrun() {
                fireStructureChange();
            }
        };
    }

    private void fireRepaint() {
        if (repaintRequested.get() > structureChangeRequested.get()) {
            ArrayList<ChildrenView<ChildrenType>> viewUpdates = new ArrayList<ChildrenView<ChildrenType>>();
            for (AbstractNode node : getTableData()) {
                if (node instanceof AbstractPackageNode) {
                    ChildrenView<ChildrenType> view = ((AbstractPackageNode) node).getView();
                    if (view.updateRequired()) {
                        viewUpdates.add(view);
                    }
                }
            }
            for (ChildrenView<ChildrenType> view : viewUpdates) {
                view.aggregate();
            }
            new EDTRunner() {
                @Override
                protected void runInEDT() {
                    /**
                     * we just want to repaint
                     */
                    if (repaintRequested.get() > structureChangeRequested.get()) {
                        try {
                            getTable().repaint();
                        } finally {
                            getTable().firePropertyChange("repaintFired", repaintFiredCounter.get(), repaintFiredCounter.incrementAndGet());
                        }
                    }
                }
            };
        }
    }

    public void fireStructureChange(boolean force) {
        if (structureChangeRequested.get() > structureChangeProcessed.getAndSet(System.currentTimeMillis()) || force) {
            try {
                _fireTableStructureChanged(getTableData(), true);
            } finally {
                getTable().firePropertyChange("structureChangedFired", structureChangedCounter.get(), structureChangedCounter.incrementAndGet());
            }
        }
    }

    private void fireStructureChange() {
        fireStructureChange(false);
    }

    public void resetSorting() {
        this.sortColumn = null;
        try {
            storage.put(ExtTableModel.SORT_ORDER_ID_KEY, (String) null);
            storage.put(ExtTableModel.SORTCOLUMN_KEY, (String) null);
        } catch (final Exception e) {
            LogController.CL(true).log(e);
        }
    }

    public ScheduledExecutorService getThreadPool() {
        return queue;
    }

    public void recreateModel(boolean delay) {
        structureChangeRequested.set(System.currentTimeMillis());
        if (delay) {
            asyncRecreate.run();
        } else {
            asyncRecreateFast.run();
        }
    }

    public void recreateModel() {
        recreateModel(true);
    }

    public void refreshModel(boolean delay) {
        repaintRequested.set(System.currentTimeMillis());
        if (delay) {
            asyncRefresh.run();
        } else {
            queue.schedule(new Runnable() {
                public void run() {
                    fireRepaint();
                }
            }, 0, TimeUnit.SECONDS);
        }
    }

    public void refreshModel() {
        refreshModel(true);
    }

    public void sortPackageChildren(final AbstractPackageNode pkg, PackageControllerComparator<ChildrenType> comparator) {
        this.resetSorting();
        pc.sortPackageChildren((PackageType) pkg, comparator);
    }

    public PackageController<PackageType, ChildrenType> getController() {
        return pc;
    }

    public void addTableModifier(TableDataModification tableModifier, boolean delay) {
        tableModifiers.add(tableModifier);
        recreateModel(delay);
    }

    public void toggleFilePackageExpand(final AbstractPackageNode fp2, final TOGGLEMODE mode) {
        final SelectionInfo<PackageType, ChildrenType> selectionInfo = getTable().getSelectionInfo(true, true);
        final boolean currentExpandedState = !fp2.isExpanded();
        tableModifiers.add(new TableDataModification() {

            @Override
            protected void modifyTableData(java.util.List<PackageType> packages) {
                boolean doToggle = true;
                switch (mode) {
                case CURRENT:
                    fp2.setExpanded(currentExpandedState);
                    break;
                case TOP:
                    doToggle = true;
                    break;
                case BOTTOM:
                    doToggle = false;
                    break;
                }
                if (mode != TOGGLEMODE.CURRENT) {
                    boolean selectionOnly = false;
                    for (PackageView<PackageType, ChildrenType> packageView : selectionInfo.getPackageViews()) {
                        if (packageView.isPackageSelected()) {
                            selectionOnly = true;
                            packageView.getPackage().setExpanded(currentExpandedState);
                        }
                    }
                    if (selectionOnly == false) {
                        for (PackageType fp : packages) {
                            if (doToggle) {
                                fp.setExpanded(currentExpandedState);
                                if (fp == fp2) {
                                    doToggle = false;
                                }
                            } else {
                                if (fp == fp2) {
                                    doToggle = true;
                                    fp.setExpanded(currentExpandedState);
                                }
                            }
                        }
                    }
                }
            }

            @Override
            protected void modifyPackageData(PackageType pkg, List<ChildrenType> unfilteredChildren) {
            }
        });
        recreateModel(false);
    }

    public void setFilePackageExpand(final boolean expanded, final AbstractPackageNode... fp2) {
        tableModifiers.add(new TableDataModification() {
            @Override
            protected void modifyTableData(java.util.List<PackageType> packages) {
                for (AbstractPackageNode fp : fp2) {
                    fp.setExpanded(expanded);
                }
            }

            @Override
            protected void modifyPackageData(PackageType pkg, List<ChildrenType> unfilteredChildren) {
            }
        });
        recreateModel(false);
    }

    private final Comparator<PackageControllerTableModelFilter<PackageType, ChildrenType>> tableFilterComparator = new Comparator<PackageControllerTableModelFilter<PackageType, ChildrenType>>() {

        public int compare(int x, int y) {
            return (x < y) ? -1 : ((x == y) ? 0 : 1);
        }

        @Override
        public int compare(PackageControllerTableModelFilter<PackageType, ChildrenType> o1, PackageControllerTableModelFilter<PackageType, ChildrenType> o2) {
            return compare(o1.getComplexity(), o2.getComplexity());
        }
    };

    public boolean isFilteredView() {
        return getTableData().isFiltered();
    }

    @Override
    public PackageControllerTableModelData<PackageType, ChildrenType> getTableData() {
        return tableData;
    }

    private final Object                                                                     tableFiltersLock = new Object();
    private volatile ArrayList<PackageControllerTableModelFilter<PackageType, ChildrenType>> tableFilters     = new ArrayList<PackageControllerTableModelFilter<PackageType, ChildrenType>>();

    public void removeFilter(PackageControllerTableModelFilter<PackageType, ChildrenType> filter) {
        if (filter != null && tableFilters.contains(filter)) {
            synchronized (tableFiltersLock) {
                final ArrayList<PackageControllerTableModelFilter<PackageType, ChildrenType>> availableTableFilters = new ArrayList<PackageControllerTableModelFilter<PackageType, ChildrenType>>(this.tableFilters);
                availableTableFilters.remove(filter);
                this.tableFilters = availableTableFilters;
            }
        }
    }

    public void addFilter(PackageControllerTableModelFilter<PackageType, ChildrenType> filter) {
        if (filter != null && !tableFilters.contains(filter)) {
            synchronized (tableFilters) {
                if (!tableFilters.contains(filter)) {
                    final ArrayList<PackageControllerTableModelFilter<PackageType, ChildrenType>> availableTableFilters = new ArrayList<PackageControllerTableModelFilter<PackageType, ChildrenType>>(this.tableFilters);
                    availableTableFilters.add(filter);
                    try {
                        Collections.sort(availableTableFilters, tableFilterComparator);
                    } catch (Throwable e) {
                        LogController.CL(true).log(e);
                    }
                    this.tableFilters = availableTableFilters;
                }
            }
        }
    }

    @Override
    protected void initColumns() {
    }

    private class CompiledFilterList {
        private final List<PackageControllerTableModelFilter<PackageType, ChildrenType>> packageFilters;

        public List<PackageControllerTableModelFilter<PackageType, ChildrenType>> getPackageFilters() {
            return packageFilters;
        }

        public List<PackageControllerTableModelFilter<PackageType, ChildrenType>> getChildrenFilters() {
            return childrenFilters;
        }

        private final List<PackageControllerTableModelFilter<PackageType, ChildrenType>> childrenFilters;

        private CompiledFilterList() {
            this(new ArrayList<PackageControllerTableModelFilter<PackageType, ChildrenType>>(), new ArrayList<PackageControllerTableModelFilter<PackageType, ChildrenType>>());
        }

        private CompiledFilterList(List<PackageControllerTableModelFilter<PackageType, ChildrenType>> packageFilters, List<PackageControllerTableModelFilter<PackageType, ChildrenType>> childrenFilters) {
            this.packageFilters = packageFilters;
            this.childrenFilters = childrenFilters;
        }

    }

    protected CompiledFilterList compileTableFilters(Collection<PackageControllerTableModelFilter<PackageType, ChildrenType>> filters) {
        if (filters == null || filters.size() == 0) {
            return new CompiledFilterList();
        }
        final ArrayList<PackageControllerTableModelFilter<PackageType, ChildrenType>> packageFilters = new ArrayList<PackageControllerTableModelFilter<PackageType, ChildrenType>>();
        final ArrayList<PackageControllerTableModelFilter<PackageType, ChildrenType>> childrendFilters = new ArrayList<PackageControllerTableModelFilter<PackageType, ChildrenType>>();
        for (PackageControllerTableModelFilter<PackageType, ChildrenType> filter : filters) {
            if (filter.isFilteringPackageNodes()) {
                packageFilters.add(filter);
            }
            if (filter.isFilteringChildrenNodes()) {
                childrendFilters.add(filter);
            }
        }
        if (packageFilters.size() > 0 || childrendFilters.size() > 0) {
            if (packageFilters.size() > 0) {
                try {
                    Collections.sort(packageFilters, tableFilterComparator);
                } catch (Throwable e) {
                    LogController.CL(true).log(e);
                }
            }
            if (childrendFilters.size() > 0) {
                try {
                    Collections.sort(childrendFilters, tableFilterComparator);
                } catch (Throwable e) {
                    LogController.CL(true).log(e);
                }
            }
        }
        return new CompiledFilterList(packageFilters, childrendFilters);
    }

    protected List<PackageControllerTableModelFilter<PackageType, ChildrenType>> getTableFilters() {
        return tableFilters;
    }

    public List<AbstractNode> refreshUnSort(final List<AbstractNode> data) {
        if (data instanceof PackageControllerTableModelData) {
            return data;
        }
        throw new IllegalArgumentException("data must be instanceof PackageControllerTableModelData");
    }

    /*
     * we override sort to have a better sorting of packages/files, to keep their structure alive,data is only used to specify the size of
     * the new ArrayList
     */
     @Override
     public java.util.List<AbstractNode> sort(final java.util.List<AbstractNode> data, ExtColumn<AbstractNode> column) {
        final boolean hideSingleChildPackages = isHideSinglePackage();
        if (column == null || column.getSortOrderIdentifier() == SORT_ORIGINAL) {
            /* RESET sorting to nothing,tri-state */
            this.sortColumn = column = null;
            try {
                storage.put(ExtTableModel.SORT_ORDER_ID_KEY, (String) null);
                storage.put(ExtTableModel.SORTCOLUMN_KEY, (String) null);
            } catch (final Exception e) {
                LogController.CL(true).log(e);
            }
        } else {
            this.sortColumn = column;
            String id = column.getSortOrderIdentifier();
            try {
                storage.put(ExtTableModel.SORT_ORDER_ID_KEY, id);
                storage.put(ExtTableModel.SORTCOLUMN_KEY, column.getID());
            } catch (final Exception e) {
                LogController.CL(true).log(e);
            }
        }
        final CompiledFilterList tableFiltersCompiled = compileTableFilters(getTableFilters());
        final ArrayList<PackageType> packages;
        if (tableFiltersCompiled.getPackageFilters().size() > 0) {
            /* filter packages */
            packages = new ArrayList<PackageType>();
            pc.visitNodes(new AbstractNodeVisitor<ChildrenType, PackageType>() {

                @Override
                public Boolean visitPackageNode(PackageType pkg) {
                    for (final PackageControllerTableModelFilter<PackageType, ChildrenType> filter : tableFiltersCompiled.getPackageFilters()) {
                        if (filter.isFiltered(pkg)) {
                            return Boolean.FALSE;
                        }
                    }
                    packages.add(pkg);
                    return Boolean.FALSE;
                }

                @Override
                public Boolean visitChildrenNode(ChildrenType node) {
                    return Boolean.FALSE;
                }
            }, true);
        } else {
            packages = pc.getPackagesCopy();
        }
        /* sort packages */
        if (column != null) {
            try {
                final ExtDefaultRowSorter<AbstractNode> comparator = column.getRowSorter();
                if (comparator != null) {
                    Collections.sort(packages, comparator);
                }
            } catch (Throwable e) {
                LogController.CL(true).log(e);
            }
        }
        final List<TableDataModification> appliedTableDataModifier;
        if (tableModifiers.size() > 0) {
            appliedTableDataModifier = new ArrayList<TableDataModification>(tableModifiers);
            tableModifiers.removeAll(appliedTableDataModifier);
            for (TableDataModification modifier : appliedTableDataModifier) {
                try {
                    modifier.modifyTableData(packages);
                } catch (Throwable e) {
                    LogController.CL(true).log(e);
                }
            }
        } else {
            appliedTableDataModifier = null;
        }
        final PackageControllerTableModelData<PackageType, ChildrenType> newData = new PackageControllerTableModelData<PackageType, ChildrenType>(Math.max(data.size(), packages.size()));
        final List<ChildrenType> visibleChildren = new ArrayList<ChildrenType>(0);
        final List<AbstractNode> filteredChildren = new ArrayList<AbstractNode>(0);
        for (final PackageType node : packages) {
            visibleChildren.clear();
            filteredChildren.clear();
            final boolean expanded = node.isExpanded();
            if (tableFiltersCompiled.getChildrenFilters().size() > 0) {
                /* filter children of this package */
                final boolean readL = node.getModifyLock().readLock();
                try {
                    childrenLoop: for (ChildrenType child : node.getChildren()) {
                        for (PackageControllerTableModelFilter<PackageType, ChildrenType> filter : tableFiltersCompiled.getChildrenFilters()) {
                            if (filter.isFiltered(child)) {
                                filteredChildren.add(child);
                                continue childrenLoop;
                            }
                        }
                        visibleChildren.add(child);
                    }
                } finally {
                    node.getModifyLock().readUnlock(readL);
                }
            } else {
                final boolean readL = node.getModifyLock().readLock();
                try {
                    visibleChildren.addAll(node.getChildren());
                } finally {
                    node.getModifyLock().readUnlock(readL);
                }
            }
            if (appliedTableDataModifier != null) {
                for (TableDataModification modifier : appliedTableDataModifier) {
                    modifier.modifyPackageData(node, visibleChildren);
                }
            }
            if (node.getView() != null) {
                if (visibleChildren.size() == 0) {
                    /* no visible/hidden children, skip PackageNode */
                    continue;
                } else {
                    node.getView().setItems(visibleChildren);
                }
            }
            if (visibleChildren.size() == 1 && hideSingleChildPackages) {
                final int visibleIndex = newData.size();
                newData.addAll(visibleChildren);
                final PackageControllerTableModelDataPackage modelDataPackage;
                if (filteredChildren.size() == 0) {
                    modelDataPackage = new PackageControllerTableModelDataPackage() {

                        @Override
                        public boolean isExpanded() {
                            return false;
                        }

                        @Override
                        public List<? extends AbstractNode> getVisibleChildren() {
                            return newData.subList(visibleIndex, visibleIndex + 1);
                        }

                        @Override
                        public List<? extends AbstractNode> getInvisibleChildren() {
                            return null;
                        }

                        @Override
                        public PackageType getPackage() {
                            return node;
                        }

                    };
                } else {
                    final int filteredIndex = newData.getFilteredChildren().size();
                    final int filteredSize = filteredChildren.size();
                    newData.getFilteredChildren().addAll(filteredChildren);
                    modelDataPackage = new PackageControllerTableModelDataPackage() {

                        @Override
                        public boolean isExpanded() {
                            return false;
                        }

                        @Override
                        public List<? extends AbstractNode> getVisibleChildren() {
                            return newData.subList(visibleIndex, visibleIndex + 1);
                        }

                        @Override
                        public List<? extends AbstractNode> getInvisibleChildren() {
                            return newData.getFilteredChildren().subList(filteredIndex, filteredIndex + filteredSize);
                        }

                        @Override
                        public PackageType getPackage() {
                            return node;
                        }

                    };
                }
                newData.getModelDataPackages().add(modelDataPackage);
            } else {
                /* only add package node if it contains children */
                newData.add(node);
                final PackageControllerTableModelDataPackage modelDataPackage;
                if (expanded) {
                    /* expanded, add its children */
                    if (column != null && visibleChildren.size() > 1) {
                        /* we only have to sort children if the package is expanded */
                        try {
                            final ExtDefaultRowSorter<AbstractNode> comparator = column.getRowSorter();
                            if (comparator != null) {
                                Collections.sort(visibleChildren, comparator);
                            }
                        } catch (final Throwable e) {
                            LogController.CL(true).log(e);
                        }
                    }
                    final int visibleIndex = newData.size();
                    newData.addAll(visibleChildren);
                    final int visibleSize = visibleChildren.size();
                    if (filteredChildren.size() == 0) {
                        modelDataPackage = new PackageControllerTableModelDataPackage() {

                            @Override
                            public boolean isExpanded() {
                                return true;
                            }

                            @Override
                            public List<? extends AbstractNode> getVisibleChildren() {
                                return newData.subList(visibleIndex, visibleIndex + visibleSize);
                            }

                            @Override
                            public List<? extends AbstractNode> getInvisibleChildren() {
                                return null;
                            }

                            @Override
                            public PackageType getPackage() {
                                return node;
                            }

                        };
                    } else {
                        final int filteredIndex = newData.getFilteredChildren().size();
                        final int filteredSize = filteredChildren.size();
                        newData.getFilteredChildren().addAll(filteredChildren);
                        modelDataPackage = new PackageControllerTableModelDataPackage() {

                            @Override
                            public boolean isExpanded() {
                                return true;
                            }

                            @Override
                            public List<? extends AbstractNode> getVisibleChildren() {
                                return newData.subList(visibleIndex, visibleIndex + visibleSize);
                            }

                            @Override
                            public List<? extends AbstractNode> getInvisibleChildren() {
                                return newData.getFilteredChildren().subList(filteredIndex, filteredIndex + filteredSize);
                            }

                            @Override
                            public PackageType getPackage() {
                                return node;
                            }

                        };
                    }
                } else {
                    final int hiddenIndex = newData.getHiddenChildren().size();
                    final int hiddenSize = visibleChildren.size();
                    newData.getHiddenChildren().addAll(visibleChildren);
                    if (filteredChildren.size() == 0) {
                        modelDataPackage = new PackageControllerTableModelDataPackage() {

                            @Override
                            public boolean isExpanded() {
                                return false;
                            }

                            @Override
                            public List<? extends AbstractNode> getVisibleChildren() {
                                return newData.getHiddenChildren().subList(hiddenIndex, hiddenIndex + hiddenSize);
                            }

                            @Override
                            public List<? extends AbstractNode> getInvisibleChildren() {
                                return null;
                            }

                            @Override
                            public PackageType getPackage() {
                                return node;
                            }

                        };
                    } else {
                        final int filteredIndex = newData.getFilteredChildren().size();
                        final int filteredSize = filteredChildren.size();
                        newData.getFilteredChildren().addAll(filteredChildren);
                        modelDataPackage = new PackageControllerTableModelDataPackage() {

                            @Override
                            public boolean isExpanded() {
                                return false;
                            }

                            @Override
                            public List<? extends AbstractNode> getVisibleChildren() {
                                return newData.getHiddenChildren().subList(hiddenIndex, hiddenIndex + hiddenSize);
                            }

                            @Override
                            public List<? extends AbstractNode> getInvisibleChildren() {
                                return newData.getFilteredChildren().subList(filteredIndex, filteredIndex + filteredSize);
                            }

                            @Override
                            public PackageType getPackage() {
                                return node;
                            }

                        };
                    }
                }
                newData.getModelDataPackages().add(modelDataPackage);
            }
        }
        final List<PackageControllerTableModelCustomizer> tableDataCustomizer = new ArrayList<PackageControllerTableModelCustomizer>();
        if (appliedTableDataModifier != null) {
            for (TableDataModification modifier : appliedTableDataModifier) {
                final PackageControllerTableModelCustomizer customizer = modifier.finalizeTableModification();
                if (customizer != null) {
                    tableDataCustomizer.add(customizer);
                }
            }
        }
        newData.setPackageFilters(tableFiltersCompiled.getPackageFilters());
        newData.setChildrenFilters(tableFiltersCompiled.getChildrenFilters());
        newData.setTableModelCustomizer(tableDataCustomizer);
        return newData;
     }

     @Override
     protected boolean postSetTableData(List<AbstractNode> newtableData) {
         boolean ret = true;
         if (!(newtableData instanceof PackageControllerTableModelData)) {
             throw new IllegalArgumentException("data must be instanceof PackageControllerTableModelData");
         } else {
             PackageControllerTableModelData<?, ?> data = (PackageControllerTableModelData<?, ?>) newtableData;
             try {
                 if (data.getTableModelCustomizer() != null) {
                     for (PackageControllerTableModelCustomizer customizer : data.getTableModelCustomizer()) {
                         ret = customizer.customizedTableData();
                     }
                 }
             } finally {
                 data.setTableModelCustomizer(null);
             }
         }
         return ret && super.postSetTableData(newtableData);
     }

     @Override
     protected void setTableData(List<AbstractNode> data) {
         if (!(data instanceof PackageControllerTableModelData)) {
             throw new IllegalArgumentException("data must be instanceof PackageControllerTableModelData");
         } else {
             tableData = (PackageControllerTableModelData) data;
             boolean vs = false;
             for (AbstractNode node : tableData) {
                 if (node instanceof AbstractPackageChildrenNode) {
                     if (((AbstractPackageChildrenNode) node).hasVariantSupport()) {
                         vs = true;
                         break;
                     }
                 }
             }
             setVariantsSupport(vs);
         }
     }

     protected void setVariantsSupport(boolean vs) {
     }

     protected ExtColumn<AbstractNode> getDefaultSortColumn() {
         return null;
     }

     protected boolean isSortStateSaverEnabled() {
         return false;
     }

     @Override
     public String getNextSortIdentifier(String sortOrderIdentifier) {
         if (!isTristateSorterEnabled()) {
             if (sortOrderIdentifier == null || sortOrderIdentifier.equals(ExtColumn.SORT_ASC)) {
                 return ExtColumn.SORT_DESC;
             } else {
                 return ExtColumn.SORT_ASC;
             }
         } else {
             if (sortOrderIdentifier == null || sortOrderIdentifier.equals(SORT_ORIGINAL)) {
                 return ExtColumn.SORT_DESC;

             } else if (sortOrderIdentifier.equals(ExtColumn.SORT_DESC)) {
                 return ExtColumn.SORT_ASC;
             } else {
                 return SORT_ORIGINAL;
             }
         }
     }

     public Icon getSortIcon(String sortOrderIdentifier) {
         if (SORT_ORIGINAL.equals(sortOrderIdentifier)) {
             return null;
         }
         return super.getSortIcon(sortOrderIdentifier);
     }

     public long getTableDataVersion() {
         return tableData.getVersion();
     }
}
