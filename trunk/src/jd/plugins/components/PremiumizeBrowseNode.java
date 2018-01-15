package jd.plugins.components;

import java.util.Map;

import org.appwork.storage.Storable;

public class PremiumizeBrowseNode implements Storable {
    private String name = null;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getUrl() {
        return link;
    }

    public boolean _isFile() {
        return "file".equals(getType());
    }

    public boolean _isDirectory() {
        return "folder".equals(getType());
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getID() {
        return this.id;
    }

    public void setID(String id) {
        this.id = id;
    }

    public Map<String, PremiumizeBrowseNode> getChildren() {
        return children;
    }

    public void setChildren(Map<String, PremiumizeBrowseNode> children) {
        this.children = children;
    }

    private long                              size     = -1;
    private String                            link     = null;
    private String                            type     = null;
    private String                            id       = null;
    private Map<String, PremiumizeBrowseNode> children = null;

    public PremiumizeBrowseNode(/* Storable */) {
    }

    @Override
    public String toString() {
        if (_isFile()) {
            return "File>Name:" + getName() + "|Size:" + getSize() + "|URL:" + getUrl();
        } else if (_isDirectory()) {
            final Map<String, PremiumizeBrowseNode> lChildren = getChildren();
            if (lChildren == null) {
                return "Dir>Name:" + getName() + "|Children:0";
            } else {
                return "Dir>Name:" + getName() + "|Children:" + children.size();
            }
        }
        return super.toString();
    }
}