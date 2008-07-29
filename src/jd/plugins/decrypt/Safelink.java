//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team jdownloader@freenet.de
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.decrypt;

import java.io.File;
import java.util.ArrayList;
import java.util.regex.Pattern;

import jd.plugins.DownloadLink;
import jd.plugins.PluginForDecrypt;

public class Safelink extends PluginForDecrypt {
    static private final String host = "safelink.in";

    private String version = "2.0.0.0";
    // rs-M2MjVTNyIjN/lvs0123.part1.rar.html
    // http://www.rapidsafe.net/rs-M2MjVTNyIjN/lvs0123.part1.rar.html
    // http://www.safelink.in/rc-UjZ4MWOwAjN/DG2.part02.rar.html
    private Pattern patternSupported = Pattern.compile("http://.*?(safelink\\.in|85\\.17\\.177\\.195)/r[cs]\\-[a-zA-Z0-9]{11}/.*", Pattern.CASE_INSENSITIVE);

    public Safelink() {
        super();
        // steps.add(new PluginStep(PluginStep.STEP_DECRYPT, null));
        // currentStep = steps.firstElement();
    }

    /*
     * Diese wichtigen Infos sollte man sich unbedingt durchlesen
     */
    @Override
    public String getCoder() {
        return "JD-Team";
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public String getPluginID() {
        return "Salfelink" + version;
    }

    @Override
    public String getPluginName() {
        return host;
    }

    @Override
    public Pattern getSupportedLinks() {
        return patternSupported;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean doBotCheck(File file) {
        return false;
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(String parameter) {
        // switch (step.getStep()) {
        // //case PluginStep.STEP_DECRYPT :
        // System.out.println(parameter);
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        progress.setRange(1);
        parameter = parameter.replaceFirst("http://.*?/r", "http://serienjunkies.org/safe/r");
        System.out.println(parameter);
        decryptedLinks.add(this.createDownloadlink(parameter));
        progress.increase(1);
        // veraltet: firePluginEvent(new PluginEvent(this,
        // PluginEvent.PLUGIN_PROGRESS_FINISH, null));
        // currentStep = null;
        // step.setParameter(decryptedLinks);

        return decryptedLinks;
    }
}
