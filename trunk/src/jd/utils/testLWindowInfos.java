//    jDownloader - Downloadmanager
//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team jdownloader@freenet.de
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program  is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSSee the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://wnu.org/licenses/>.


package jd.utils;

import jd.utils.LWindowInfos.WindowInformations;

public class testLWindowInfos {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		WindowInformations[] infos = LWindowInfos.getWindowInfos();
		for (int i = 0; i < infos.length; i++) {
			System.out.println(infos[i].toString());
		}
	}

}
