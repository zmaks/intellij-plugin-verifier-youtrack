package com.jetbrains.pluginverifier.utils;

import com.google.common.base.Throwables;
import com.google.common.net.HttpHeaders;
import com.jetbrains.pluginverifier.problems.UpdateInfo;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * @author Sergey Evdokimov
 */
public class DownloadUtils {

  private static final DateFormat httpDateFormat;
  static {
    httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    httpDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private static File getOrCreateDownloadDir() throws IOException {
    File downloadDir = Util.getPluginCacheDir();
    if (!downloadDir.isDirectory()) {
      downloadDir.mkdirs();
      if (!downloadDir.isDirectory()) {
        throw new IOException("Failed to create temp directory: " + downloadDir);
      }
    }

    return downloadDir;
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public static File getCheckResultFile(String build) throws IOException {
    File downloadDir = getOrCreateDownloadDir();

    File checkResDir = new File(downloadDir, "checkResult");
    checkResDir.mkdirs();

    File res = new File(checkResDir, build + ".xml");

    updateFile(new URL(Configuration.getInstance().getPluginRepositoryUrl() + "/files/checkResults/" + build + ".xml"), res);

    return res;
  }

  @SuppressWarnings({"ResultOfMethodCallIgnored", "StatementWithEmptyBody"})
  private static void updateFile(URL url, File file) throws IOException {
    long lastModified = file.lastModified();

    HttpURLConnection connection = (HttpURLConnection)url.openConnection();

    if (lastModified > 0) {
      connection.addRequestProperty(HttpHeaders.IF_MODIFIED_SINCE, httpDateFormat.format(new Date(lastModified)));
      connection.addRequestProperty(HttpHeaders.CACHE_CONTROL, "max-age=0");
    }

    int responseCode = connection.getResponseCode();

    try {
      if (responseCode == 200) {
        String lastModifiedResStr = connection.getHeaderField(HttpHeaders.LAST_MODIFIED);
        if (lastModifiedResStr == null) {
          throw new IOException(HttpHeaders.LAST_MODIFIED + " header can not be null");
        }

        Date lastModifiedRes;

        try {
          lastModifiedRes = httpDateFormat.parse(lastModifiedResStr);
        }
        catch (ParseException e) {
          throw Throwables.propagate(e);
        }

        FileUtils.copyInputStreamToFile(connection.getInputStream(), file);
        file.setLastModified(lastModifiedRes.getTime());
      }
      else if (responseCode == 304) {
        // Not modified
      }
      else {
        throw new IOException("Failed to download check result: " + responseCode);
      }
    }
    finally {
      connection.disconnect();
    }
  }

  private static String getCacheFileName(UpdateInfo update) {
    if (update.getUpdateId() != null) {
      return update.getUpdateId() + ".zip";
    }
    else {
      String updateAndVersion = update.getPluginId() + ":" + update.getVersion();
      return (updateAndVersion + '_' + Integer.toHexString(updateAndVersion.hashCode()) + ".zip").replaceAll("[^a-zA-Z0-9_\\-.]+", "_");
    }
  }

  /**
   * Performs necessary redirection
   */
  @NotNull
  private static URL getFinalUrl(@NotNull URL url) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setInstanceFollowRedirects(false);

    if (connection.getResponseCode() == HttpURLConnection.HTTP_ACCEPTED) {
      return url;
    }

    if (connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP
        || connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM) {
      String location = connection.getHeaderField("Location");
      if (location != null) {
        return new URL(location);
      }
    }
    return url;
  }

  @NotNull
  public static File getOrLoadUpdate(@NotNull UpdateInfo update, @NotNull URL url) throws IOException {
    File downloadDir = DownloadUtils.getOrCreateDownloadDir();

    url = getFinalUrl(url);

    File pluginInCache = new File(downloadDir, getCacheFileName(update));

    if (!pluginInCache.exists()) {
      File currentDownload = File.createTempFile("currentDownload", ".zip", downloadDir);

      System.out.println("Downloading " + update + "... ");

      boolean downloadFail = true;
      try {
        FileUtils.copyURLToFile(url, currentDownload);

        if (currentDownload.length() < 200) {
          throw new IOException("Broken zip archive");
        }

        System.out.println("downloading " + update + " done!");
        downloadFail = false;
      } catch (IOException e) {
        System.out.println("Error loading plugin " + update + " " + e.getLocalizedMessage());
        e.printStackTrace();
        throw e;

      } finally {
        if (downloadFail) {
          if (currentDownload.exists()) {
            //noinspection ResultOfMethodCallIgnored
            currentDownload.delete();
          }
        }
      }
      if (pluginInCache.exists()) {
        //noinspection ResultOfMethodCallIgnored
        pluginInCache.delete();
      }
      FileUtils.moveFile(currentDownload, pluginInCache);
    }

    return pluginInCache;
  }

}
