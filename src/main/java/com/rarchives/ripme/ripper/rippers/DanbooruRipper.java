package com.rarchives.ripme.ripper.rippers;

import com.rarchives.ripme.ripper.utilities.AbstractJSONRipper;

import com.rarchives.ripme.ripper.utilities.DownloadFileOkHttpClientThread;
import com.rarchives.ripme.ui.RipStatusMessage;
import com.rarchives.ripme.utils.Utils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DanbooruRipper extends AbstractJSONRipper {
    private static final String DOMAIN = "danbooru.donmai.us",
            HOST = "danbooru";
    private final OkHttpClient httpClient;

    private Pattern gidPattern = null;

    private int currentPageNum = 1;

    public DanbooruRipper(URL url) throws IOException {
        super(url);
        this.httpClient = new OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override
    protected String getDomain() {
        return DOMAIN;
    }

    @Override
    public String getHost() {
        return HOST;
    }

    private String getPage(int num) throws MalformedURLException {
        return "https://" + getDomain() + "/posts.json?page=" + num + "&tags=" + getTag(url);
    }

    private final String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:65.0) Gecko/20100101 Firefox/65.0";
    @Override
    protected JSONObject getFirstPage() throws MalformedURLException {
        return getCurrentPage();
    }

    @Override
    protected JSONObject getNextPage(JSONObject doc) throws IOException {
        return getCurrentPage();
    }

    @Nullable
    private JSONObject getCurrentPage() throws MalformedURLException {
        Request request = new Request.Builder()
                .url(getPage(currentPageNum))
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1")
                .header("Accept", "application/json,text/javascript,*/*;q=0.01")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Referer", "https://danbooru.donmai.us/")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Connection", "keep-alive")
                .build();
        Response response = null;
        currentPageNum++;
        try {
            response = httpClient.newCall(request).execute();
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            String responseData = response.body().string();
            JSONArray jsonArray = new JSONArray(responseData);
            if(!jsonArray.isEmpty()){
                String newCompatibleJSON = "{ \"resources\":" + jsonArray + " }";
                return new JSONObject(newCompatibleJSON);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(response !=null) {
                response.body().close();
            }
        }
        return null;
    }

    @Override
    protected List<String> getURLsFromJSON(JSONObject json) {
        List<String> res = new ArrayList<>(100);
        JSONArray jsonArray = json.getJSONArray("resources");
        for (int i = 0; i < jsonArray.length(); i++) {
            if (jsonArray.getJSONObject(i).has("file_url")) {
                res.add(jsonArray.getJSONObject(i).getString("file_url"));
            }
        }
        return res;
    }

    @Override
    public String getGID(URL url) throws MalformedURLException {
        try {
            return Utils.filesystemSafe(new URI(getTag(url).replaceAll("([?&])tags=", "")).getPath());
        } catch (URISyntaxException ex) {
            LOGGER.error(ex);
        }

        throw new MalformedURLException("Expected booru URL format: " + getDomain() + "/posts?tags=searchterm - got " + url + " instead");
    }

    @Override
    protected void downloadURL(URL url, int index) {
        addURLToDownload(url, getPrefix(index));
    }

    @Override
    public boolean addURLToDownload(URL url, Path saveAs, String referrer, Map<String,String> cookies, Boolean getFileExtFromMIME) {
        // Only download one file if this is a test.
        if (super.isThisATest() && (itemsCompleted.size() > 0 || itemsErrored.size() > 0)) {
            stop();
            itemsPending.clear();
            return false;
        }
        if (!allowDuplicates()
                && ( itemsPending.containsKey(url)
                || itemsCompleted.containsKey(url)
                || itemsErrored.containsKey(url) )) {
            // Item is already downloaded/downloading, skip it.
            LOGGER.info("[!] Skipping " + url + " -- already attempted: " + Utils.removeCWD(saveAs));
            return false;
        }
        if (shouldIgnoreURL(url)) {
            sendUpdate(RipStatusMessage.STATUS.DOWNLOAD_SKIP, "Skipping " + url.toExternalForm() + " - ignored extension");
            return false;
        }
        if (Utils.getConfigBoolean("urls_only.save", false)) {
            // Output URL to file
            Path urlFile = Paths.get(this.workingDir + "/urls.txt");
            String text = url.toExternalForm() + System.lineSeparator();
            try {
                Files.write(urlFile, text.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                itemsCompleted.put(url, urlFile);
            } catch (IOException e) {
                LOGGER.error("Error while writing to " + urlFile, e);
            }
        }
        else {
            itemsPending.put(url, saveAs.toFile());
            DownloadFileOkHttpClientThread dft = new DownloadFileOkHttpClientThread(url,  saveAs.toFile(),  this, getFileExtFromMIME, this.httpClient);
            if (referrer != null) {
                dft.setReferrer(referrer);
            }
            if (cookies != null) {
                dft.setCookies(cookies);
            }
            threadPool.addThread(dft);
        }

        return true;
    }

    private String getTag(URL url) throws MalformedURLException {
        gidPattern = Pattern.compile("https?://danbooru.donmai.us/(posts)?.*([?&]tags=([^&]*)(?:&z=([0-9]+))?$)");
        Matcher m = gidPattern.matcher(url.toExternalForm());

        if (m.matches()) {
            return m.group(3);
        }

        throw new MalformedURLException("Expected danbooru URL format: " + getDomain() + "/posts?tags=searchterm - got " + url + " instead");
    }

}
