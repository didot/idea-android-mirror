/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.profilers.network;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Data of http url connection. Each {@code HttpData} object matches a http connection with a unique id, and it includes both request data
 * and response data. Request data is filled immediately when the connection starts. Response data may be empty, it will filled when
 * connection completes.
 */
public class HttpData {
  // TODO: Way more robust handling of different types. See also:
  // http://www.iana.org/assignments/media-types/media-types.xhtml
  private static final Map<String, String> CONTENT_EXTENSIONS_MAP = new ImmutableMap.Builder<String, String>()
    .put("/html", ".html")
    .put("/jpeg", ".jpg")
    .put("/json", ".json")
    .put("/xml", ".xml")
    .build();

  public static final String FIELD_CONTENT_TYPE = "Content-Type";
  public static final String FIELD_CONTENT_LENGTH = "Content-Length";
  public static final int NO_STATUS_CODE = -1;

  private final long myId;
  private final long myStartTimeUs;
  private final long myEndTimeUs;
  private final long myDownloadingTimeUs;
  @NotNull private final String myUrl;
  @NotNull private final String myMethod;
  @NotNull private final String myTrace;

  @Nullable private final String myResponsePayloadId;

  private int myStatusCode = NO_STATUS_CODE;
  private final Map<String, String> myResponseFields = new HashMap<>();
  private final Map<String, String> myRequestFields = new HashMap<>();
  // TODO: Move it to datastore, for now virtual file creation cannot select file type.
  private File myResponsePayloadFile;

  private HttpData(@NotNull Builder builder) {
    myId = builder.myId;
    myStartTimeUs = builder.myStartTimeUs;
    myEndTimeUs = builder.myEndTimeUs;
    myDownloadingTimeUs = builder.myDownloadingTimeUs;
    myUrl = builder.myUrl;
    myMethod = builder.myMethod;
    myTrace = builder.myTrace;
    myResponsePayloadId = builder.myResponsePayloadId;

    if (builder.myResponseFields != null) {
      parseResponseFields(builder.myResponseFields);
    }
    if (builder.myRequestFields != null) {
      parseRequestFields(builder.myRequestFields);
    }
  }

  public long getId() {
    return myId;
  }

  public long getStartTimeUs() {
    return myStartTimeUs;
  }

  public long getEndTimeUs() {
    return myEndTimeUs;
  }

  public long getDownloadingTimeUs() {
    return myDownloadingTimeUs;
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  public String getMethod() {
    return myMethod;
  }

  @NotNull
  public String getTrace() { return myTrace; }

  @Nullable
  public String getResponsePayloadId() {
    return myResponsePayloadId;
  }

  public int getStatusCode() {
    return myStatusCode;
  }

  @Nullable
  public File getResponsePayloadFile() {
    return myResponsePayloadFile;
  }

  public void setResponsePayloadFile(@NotNull File payloadFile) {
    myResponsePayloadFile = payloadFile;
  }

  @Nullable
  public String getResponseField(@NotNull String field) {
    return myResponseFields.get(field);
  }

  @Nullable
  public ContentType getContentType() {
    String type = getResponseField(FIELD_CONTENT_TYPE);
    return (type == null) ? null : new ContentType(type);
  }

  @NotNull
  public ImmutableMap<String, String> getResponseHeaders() {
    return ImmutableMap.copyOf(myResponseFields);
  }

  @NotNull
  public ImmutableMap<String, String> getRequestHeaders() {
    return ImmutableMap.copyOf(myRequestFields);
  }

  private void parseResponseFields(@NotNull String fields) {
    // The status-line - should be formatted as per
    // section 6.1 of RFC 2616.
    // https://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html
    //
    // Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase
    String[] firstLineSplit = fields.trim().split("\\n", 2);
    String status = firstLineSplit[0].trim();
    fields = firstLineSplit.length > 1 ? firstLineSplit[1] : "";
    if (!status.isEmpty()) {
      String[] tokens = status.split("=", 2);
      status = tokens[tokens.length - 1].trim();
      assert status.startsWith("HTTP/1.") : String.format("Unexpected http response status-line (%s)", status);
      myStatusCode = Integer.parseInt(status.split(" ")[1]);
    }

    parseHeaderFields(fields, myResponseFields);
  }

  private void parseRequestFields(@NotNull String fields) {
    parseHeaderFields(fields, myRequestFields);
  }

  private static void parseHeaderFields(@NotNull String fields, @NotNull Map<String, String> map) {
    map.clear();
    Arrays.stream(fields.split("\\n")).filter(line -> !line.trim().isEmpty()).forEach(line -> {
      String[] keyAndValue = line.split("=", 2);
      assert keyAndValue.length == 2 : String.format("Unexpected http header field (%s)", line);
      map.put(keyAndValue[0].trim(), StringUtil.trimEnd(keyAndValue[1].trim(), ';'));
    });
  }

  /**
   * Return the name of the URL, which is the final complete word in the path portion of the URL. Additionally,
   * the returned value is URL decoded, so that, say, "Hello%2520World" -> "Hello World".
   *
   * For example,
   * "www.example.com/demo/" -> "demo"
   * "www.example.com/test.png?res=2" -> "test.png"
   * "www.example.com" -> "www.example.com"
   */
  @NotNull
  public static String getUrlName(@NotNull String url) {
    URI uri = URI.create(url);
    if (uri.getPath().isEmpty()) {
      return uri.getHost();
    }
    String path = StringUtil.trimTrailing(uri.getPath(), '/');
    path = path.lastIndexOf('/') != -1 ? path.substring(path.lastIndexOf('/') + 1) : path;
    // URL might be encoded an arbitrarily deep number of times. Keep decoding until we peel away the final layer.
    // Usually this is only expected to loop once or twice.
    // See more: http://stackoverflow.com/questions/3617784/plus-signs-being-replaced-for-252520
    try {
      String lastPath;
      do {
        lastPath = path;
        path = URLDecoder.decode(path, "UTF-8");
      } while (!path.equals(lastPath));
    } catch (Exception ignored) {
    }
    return path;
  }

  public static final class ContentType {
    @NotNull private final String myContentType;

    public ContentType(@NotNull String contentType) {
      myContentType = contentType;
    }

    /**
     * @return MIME type related information from Content-Type because Content-Type may contain
     * other information such as charset or boundary.
     *
     * Examples:
     * "text/html; charset=utf-8" => "text/html"
     * "text/html" => "text/html"
     */
    @NotNull
    public String getMimeType() {
      return myContentType.split(";")[0];
    }

    /**
     * Returns file extension based on the response Content-Type header field.
     * If type is absent or not supported, returns null.
     */
    @Nullable
    public String guessFileExtension() {
      for (Map.Entry<String, String> entry : CONTENT_EXTENSIONS_MAP.entrySet()) {
        if (myContentType.contains(entry.getKey())) {
          return entry.getValue();
        }
      }
      return null;
    }

    @NotNull
    public String getContentType() {
      return myContentType;
    }

    @Override
    public String toString() {
      return getContentType();
    }
  }

  public static final class Builder {
    private final long myId;
    private final long myStartTimeUs;
    private final long myEndTimeUs;
    private final long myDownloadingTimeUs;

    private String myUrl;
    private String myMethod;

    private String myResponseFields;
    private String myRequestFields;
    private String myResponsePayloadId;
    private String myTrace;

    public Builder(long id, long startTimeUs, long endTimeUs, long downloadingTimeUS) {
      myId = id;
      myStartTimeUs = startTimeUs;
      myEndTimeUs = endTimeUs;
      myDownloadingTimeUs = downloadingTimeUS;
    }

    @NotNull
    public Builder setUrl(@NotNull String url) {
      myUrl = url;
      return this;
    }

    @NotNull
    public Builder setMethod(@NotNull String method) {
      myMethod = method;
      return this;
    }

    @NotNull
    public Builder setTrace(@NotNull String trace) {
      myTrace = trace;
      return this;
    }

    @NotNull
    public Builder setResponseFields(@NotNull String responseFields) {
      myResponseFields = responseFields;
      return this;
    }

    @NotNull
    public Builder setResponsePayloadId(@NotNull String payloadId) {
      myResponsePayloadId = payloadId;
      return this;
    }

    @NotNull
    public Builder setRequestFields(@NotNull String requestFields) {
      myRequestFields = requestFields;
      return this;
    }

    @NotNull
    public HttpData build() {
      return new HttpData(this);
    }
  }
}
