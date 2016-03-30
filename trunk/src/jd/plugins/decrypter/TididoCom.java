//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;
import jd.plugins.hoster.DummyScriptEnginePlugin;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "tidido.com" }, urls = { "https?://(?:www\\.)?tidido\\.com/((?:[a-z]{2}/)?a[a-f0-9]+(?:/al[a-f0-9]+)?(?:/t[a-f0-9]+)?|[A-Za-z]{2}/moods/[^/]+/[a-f0-9]+)" }, flags = { 0 })
public class TididoCom extends PluginForDecrypt {

    public TididoCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    private final String TYPE_ALBUM    = "https?://(?:www\\.)?tidido\\.com/(?:[a-z]{2}/)?a[a-z0-9]+/al[a-z0-9]+";
    private final String TYPE_PLAYLIST = "https?://(?:www\\.)?tidido\\.com/([A-Za-z]{2})/moods/([^/]+)/([a-z0-9]+)";
    private final String TYPE_SONG     = "https?://(?:www\\.)?tidido\\.com/(?:[a-z]{2}/)?a[a-z0-9]+/al[a-z0-9]+/t[a-z0-9]+";
    private final String TYPE_ARTIST   = "https?://(?:www\\.)?tidido\\.com/(?:[a-z]{2}/)?a[a-z0-9]+";

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        String name_playlist_url = null;
        String name_playlist = null;

        String fpName = null;

        String target_song_id = null;
        String album_id = null;
        String artist_id = null;
        String playlist_id = null;
        String playlist_areaname = null;
        if (parameter.matches(TYPE_SONG)) {
            album_id = new Regex(parameter, "al([a-z0-9]+)").getMatch(0);
            artist_id = new Regex(parameter, "a([a-z0-9]+)").getMatch(0);
            target_song_id = new Regex(parameter, "t([^/]+)$").getMatch(0);
        } else if (parameter.matches(TYPE_ALBUM)) {
            album_id = new Regex(parameter, "al([a-z0-9]+)").getMatch(0);
            artist_id = new Regex(parameter, "a([a-z0-9]+)").getMatch(0);
        } else if (parameter.matches(TYPE_ARTIST)) {
            artist_id = new Regex(parameter, "a([a-z0-9]+)").getMatch(0);
        } else if (parameter.matches(TYPE_PLAYLIST)) {
            playlist_areaname = new Regex(parameter, TYPE_PLAYLIST).getMatch(0);
            name_playlist_url = new Regex(parameter, TYPE_PLAYLIST).getMatch(1);
            playlist_id = new Regex(parameter, TYPE_PLAYLIST).getMatch(2);
        } else {
            /* Unsupported linktype */
            return decryptedLinks;
        }

        LinkedHashMap<String, String> artist_info = new LinkedHashMap<String, String>();
        LinkedHashMap<String, Object> entries = null;
        LinkedHashMap<String, Object> entries2 = null;
        ArrayList<Object> ressourcelist = null;
        ArrayList<Object> song_array = null;
        if (parameter.matches(TYPE_PLAYLIST)) {
            this.br.getPage("http://tidido.com/api/music/mood/" + name_playlist_url + "?areaname=" + playlist_areaname + "&playlists=true");
            entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(br.toString());
            ressourcelist = (ArrayList) DummyScriptEnginePlugin.walkJson(entries, "playlists_data/playlists");
            /* Find song_ids of our playlist ... */
            for (final Object playlisto : ressourcelist) {
                entries2 = (LinkedHashMap<String, Object>) playlisto;
                final String playlistid_tmp = (String) entries2.get("id");
                final String playlistname = (String) entries2.get("name");
                if (playlistid_tmp == null || playlistname == null) {
                    continue;
                }
                if (playlistid_tmp.equals(playlist_id)) {
                    name_playlist = playlistname;
                    break;
                }
            }
            fpName = name_playlist;
            /* Collect song_ids to access all via URL */
            String song_ids_url = "/api/music/song?sids=";
            ressourcelist = (ArrayList) entries2.get("sids");
            for (final Object songido : ressourcelist) {
                song_ids_url += (String) songido + "_";
            }
            this.br.getPage(song_ids_url);
            entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(br.toString());
            song_array = (ArrayList) entries.get("songs");
        } else {
            if (album_id != null) {
                this.br.getPage("http://tidido.com/api/music/album/" + album_id);
                entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(br.toString());
                song_array = (ArrayList) DummyScriptEnginePlugin.walkJson(entries, "songs/songs");
            } else {
                this.br.getPage("http://tidido.com/api/gene/artist/" + artist_id + "?section=topSongs&limit=1000");
                entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(br.toString());
                song_array = (ArrayList) entries.get("songs");
            }
        }
        /* First fill artist_info */
        ressourcelist = (ArrayList) entries.get("artists");
        for (final Object artisto : ressourcelist) {
            entries2 = (LinkedHashMap<String, Object>) artisto;
            final String artistid = (String) entries2.get("id");
            final String artistname = (String) entries2.get("fullname");
            if (artistid == null || artistname == null) {
                continue;
            }
            if (fpName == null) {
                /* TODO: Maybe improve packagename in the future */
                fpName = artistname;
            }
            artist_info.put(artistid, artistname);
        }
        for (final Object songo : song_array) {
            entries2 = (LinkedHashMap<String, Object>) songo;
            String artist = null;
            final long artistid = DummyScriptEnginePlugin.toLong(DummyScriptEnginePlugin.walkJson(entries2, "artistIds/{0}"), 0);
            final String songid = (String) entries2.get("id");
            final String songname = (String) entries2.get("name");
            final String directlink = (String) entries2.get("url");
            final long bitrate = DummyScriptEnginePlugin.toLong(entries2.get("bitrate"), 0);
            if (songid == null || songname == null || bitrate == 0) {
                continue;
            }
            String filename_temp = null;
            String filename = null;
            if (artistid != 0 && artist_info.containsKey(Long.toString(artistid))) {
                artist = artist_info.get(Long.toString(artistid));
                filename = artist + " - " + songname;
            } else {
                filename = songname;
            }
            filename = encodeUnicode(filename);
            /* Include bitrate in temp filename but not in final filename. */
            filename_temp = filename + "_" + bitrate + ".mp3";
            filename += ".mp3";

            final String content_url = "http://tidido.com/a" + artistid + "/al" + album_id + "/t" + songid;
            final String content_url_decrypted = "http://tididodecrypted.com/a" + artistid + "/al" + album_id + "/t" + songid;
            final DownloadLink dl = createDownloadlink(content_url_decrypted);
            dl.setContentUrl(content_url);
            dl.setAvailable(true);
            dl.setName(filename_temp);
            dl.setProperty("decryptedfilename", filename);
            dl.setProperty("directlink", directlink);
            if (target_song_id != null && songid.equals(target_song_id)) {
                /* We were looking for one specified track only - remove previously added data! */
                decryptedLinks.clear();
                decryptedLinks.add(dl);
                break;
            } else {
                /* Simply add track */
                decryptedLinks.add(dl);
            }

        }

        final FilePackage fp = FilePackage.getInstance();
        fp.setName(Encoding.htmlDecode(fpName.trim()));
        fp.addLinks(decryptedLinks);

        return decryptedLinks;
    }

    /** Avoid chars which are not allowed in filenames under certain OS' */
    private static String encodeUnicode(final String input) {
        String output = input;
        output = output.replace(":", ";");
        output = output.replace("|", "¦");
        output = output.replace("<", "[");
        output = output.replace(">", "]");
        output = output.replace("/", "⁄");
        output = output.replace("\\", "∖");
        output = output.replace("*", "#");
        output = output.replace("?", "¿");
        output = output.replace("!", "¡");
        output = output.replace("\"", "'");
        return output;
    }

}
