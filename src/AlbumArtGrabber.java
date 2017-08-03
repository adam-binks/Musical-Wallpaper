import com.wrapper.spotify.Api;
import com.wrapper.spotify.exceptions.WebApiException;
import com.wrapper.spotify.methods.PlaylistTracksRequest;
import com.wrapper.spotify.methods.authentication.ClientCredentialsGrantRequest;
import com.wrapper.spotify.models.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

/// Downloads the album art for each song in the playlist
public class AlbumArtGrabber extends SwingWorker<Void, Void> { //  extends SwingWorker<Void, Void> {
    final int MAX_TRACKS_FROM_PLAYLIST = 100; // spotify enforces this limit
    final static int[] SPOTIFY_IMAGE_SIZES = new int[] { 640, 300, 64 };
    final private String DIRECTORY = System.getProperty("user.home") + File.separator + "Pictures"
                    + File.separator + "Musical Wallpaper" + File.separator + "Album art";

    protected String errorCode = null; // only non-null if an exception was encountered downloading album art
    // the error code is to get around SwingWorkers not using exception throwing in doInBackground()


    @Override
    protected Void doInBackground() {
        try {
            downloadAlbumArt();
        } catch(Exception e) {
            errorCode = "Could not download album art. Error: " + e.getMessage();
        }
        return null;
    }

    public void downloadAlbumArt()
            throws IOException, WebApiException, InvalidPlaylistURLException {
        Api api = getAuthorisedAPI();
        String[] IDs = PlaylistIDManager.getPlaylistIDAndUserIDFromURL(PropertiesManager.getProperty("playlistURL"));
        String playlistID = IDs[0];
        String userID = IDs[1];
        HashMap<String, String> albumNamesAndImageURLs = getAlbumImagesInPlaylist(playlistID, userID, api);
        setProgress(20); // move the progress bar a bit

        // clear any previously downloaded album art
        new File(DIRECTORY).mkdirs(); // make sure the DIRECTORY exists
        for (File file : new File(DIRECTORY).listFiles()) {
            file.delete();
        }
        downloadAlbumsToDirectory(albumNamesAndImageURLs, DIRECTORY);
        setProgress(40); // move the progress bar a bit
    }

    /// Returns a HashMap mapping album names to their biggest image's URL, for each album in the given playlist
    private HashMap<String, String> getAlbumImagesInPlaylist(String playlistID, String userID, Api api)
            throws IOException, WebApiException {
        HashMap<String, String> albumNamesAndImages = new HashMap<>();

        // Spotify enforces a maximum of 100 tracks retrieved from a playlist per request.
        // Therefore this method executes multiple requests in the following for loop

        // calculate the number of loops required to get all of the tracks from the playlist
        Playlist playlist = api.getPlaylist(userID, playlistID).build().get();
        int numTracks = playlist.getTracks().getTotal();
        int loopsRequired = numTracks / 100 + 1;  // round up

        for (int i = 0; i <= loopsRequired; i++) {
            PlaylistTracksRequest playlistTracksRequest = api.getPlaylistTracks(userID, playlistID)
                    .limit(MAX_TRACKS_FROM_PLAYLIST)  // maximum number of tracks to return
                    // if this isn't the first loop, don't start at the top of the playlist
                    .offset(i * MAX_TRACKS_FROM_PLAYLIST)
                    .build();

            List<PlaylistTrack> tracks = playlistTracksRequest.get().getItems();
            for (PlaylistTrack track : tracks) {
                SimpleAlbum album = track.getTrack().getAlbum();
                // use IMAGE_NUMBER to select the desired resolution
                int imageNum = Integer.parseInt(PropertiesManager.getProperty("imageSizeCode"));
                String url = album.getImages().get(imageNum).getUrl();
                albumNamesAndImages.put(album.getName(), url);
            }
        }


        return albumNamesAndImages;
    }

    private Api getAuthorisedAPI() throws IOException, WebApiException {
        // TODO hide these somehow?
        String clientID = "e1706c058e6b4cf0b282597ad0022b1e";
        String clientSecret = "bde6b1541a9648a6ab763c05d26bf299";
        String redirectURI = "https://github.com/adam-binks";

        Api api = Api.builder()
                .clientId(clientID)
                .clientSecret(clientSecret)
                .redirectURI(redirectURI)
                .build();

        // Use the Client Credentials flow which limits access but does not require user login
        ClientCredentialsGrantRequest request = api.clientCredentialsGrant().build();
        ClientCredentials credentials = request.get();
        String accessToken = credentials.getAccessToken();
        api.setAccessToken(accessToken); // store the access token in Api

        return api;
    }

    private void downloadAlbumsToDirectory(HashMap<String, String> albumNamesAndImages, String directory)
            throws IOException {
        for (String album : albumNamesAndImages.keySet()) {
            // create a file in the DIRECTORY, named after the album
            String cleanedAlbum = getCleanedFilename(album); // remove invalid characters
            String path = directory + File.separator + cleanedAlbum + ".jpg";
            File file = new File(path);
            // if this album has already been downloaded, skip it
            if (file.exists()) {
                continue;
            }
            // if the DIRECTORY does not exist, create it
            file.mkdirs();

            // download the image
            URL url = new URL(albumNamesAndImages.get(album));
            BufferedImage image = ImageIO.read(url);

            // write the downloaded image to the file
            ImageIO.write(image, "jpg", file);
        }
    }


    private String getCleanedFilename(String oldFilename) {
        StringBuilder filename = new StringBuilder();

        for (char c : oldFilename.toCharArray()) {
            // only allow java identifiers into the filename
            // this is a bit conservative but that shouldn't matter
            if (c == ' ' | Character.isJavaIdentifierPart(c)) {
                filename.append(c);
            } else {
                // invalid characters are replaced by hyphens
                filename.append('-');
            }
        }

        return filename.toString();
    }
}
