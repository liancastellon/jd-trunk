package jd.router.upnp;

/******************************************************************
 *
 *	CyberUPnP for Java
 *
 *	Copyright (C) Satoshi Konno 2002
 *
 *	File : ArgumentTable.java
 *
 ******************************************************************/

import org.cybergarage.upnp.Argument;

public class ArgumentTable extends TableModel {
    private static final long serialVersionUID = 1L;

    public ArgumentTable(Argument arg) {
        super(arg.getArgumentNode());
    }
}
