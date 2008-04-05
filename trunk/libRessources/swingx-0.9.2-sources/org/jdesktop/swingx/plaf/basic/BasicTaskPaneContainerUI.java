/*
 * $Id: BasicTaskPaneContainerUI.java,v 1.6 2007/10/31 13:24:06 kschaefe Exp $
 *
 * Copyright 2004 Sun Microsystems, Inc., 4150 Network Circle,
 * Santa Clara, California 95054, U.S.A. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.jdesktop.swingx.plaf.basic;

import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.plaf.ComponentUI;

import org.jdesktop.swingx.JXTaskPaneContainer;
import org.jdesktop.swingx.VerticalLayout;
import org.jdesktop.swingx.plaf.LookAndFeelAddons;
import org.jdesktop.swingx.plaf.TaskPaneContainerUI;

/**
 * Base implementation of the <code>JXTaskPaneContainer</code> UI.
 * 
 * @author <a href="mailto:fred@L2FProd.com">Frederic Lavigne</a>
 * @author Karl Schaefer
 */
public class BasicTaskPaneContainerUI extends TaskPaneContainerUI {

  /**
   * Returns a new instance of BasicTaskPaneContainerUI.
   * BasicTaskPaneContainerUI delegates are allocated one per
   * JXTaskPaneContainer.
   * 
   * @return A new TaskPaneContainerUI implementation for the Basic look and
   *         feel.
   */
  public static ComponentUI createUI(JComponent c) {
    return new BasicTaskPaneContainerUI();
  }

  /**
   * The task pane container managed by this UI delegate.
   */
  protected JXTaskPaneContainer taskPane;

  /**
   * {@inheritDoc}
   */
  public void installUI(JComponent c) {
    super.installUI(c);
    taskPane = (JXTaskPaneContainer)c;
    installDefaults();
    taskPane.setLayout(new VerticalLayout(14));
  }

    /**
     * Installs the default colors, border, and painter of the task pane
     * container.
     */
    protected void installDefaults() {
        LookAndFeel.installColors(taskPane, "TaskPaneContainer.background",
                "TaskPaneContainer.foreground");
        LookAndFeel.installBorder(taskPane, "TaskPaneContainer.border");
        LookAndFeelAddons.installBackgroundPainter(taskPane,
                "TaskPaneContainer.backgroundPainter");
        LookAndFeel.installProperty(taskPane, "opaque", Boolean.TRUE);
    }
    
    /**
     * {@inheritDoc}
     */
    public void uninstallUI(JComponent c) {
        uninstallDefaults();
        
        super.uninstallUI(c);
    }

    /**
     * Uninstalls the default colors, border, and painter of the task pane
     * container.
     */
    protected void uninstallDefaults() {
        LookAndFeel.uninstallBorder(taskPane);
        LookAndFeelAddons.uninstallBackgroundPainter(taskPane);
    }
}
