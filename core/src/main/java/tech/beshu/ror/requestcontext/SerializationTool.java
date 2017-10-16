/*
 *    This file is part of ReadonlyREST.
 *
 *    ReadonlyREST is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    ReadonlyREST is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with ReadonlyREST.  If not, see http://www.gnu.org/licenses/
 */

package tech.beshu.ror.requestcontext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import tech.beshu.ror.commons.ResponseContext;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by sscarduzio on 28/06/2017.
 */
public class SerializationTool {
  private final static SimpleDateFormat zuluFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
  private final static SimpleDateFormat indexNameFormatter = new SimpleDateFormat("yyyy-MM-dd");
  private static ObjectMapper mapper;
  private final static Set<String> whitelistedHeaders;

  static {
    zuluFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

    if (System.getProperty("whitelistedHeaders") != null) {
      whitelistedHeaders = Sets.newHashSet(System.getProperty("whitelistedHeaders").split(","));
    } else {
      whitelistedHeaders = new HashSet<>();
    }
  }

  public SerializationTool() {
    ObjectMapper mapper = new ObjectMapper();
    SimpleModule simpleModule = new SimpleModule(
      "SimpleModule",
      new Version(1, 0, 0, null, "com.readonlyrest", "readonlyrest")
    );
    mapper.registerModule(simpleModule);
    this.mapper = mapper;
  }

  public String mkIndexName() {
    return "readonlyrest_audit-" + indexNameFormatter.format(Calendar.getInstance().getTime());
  }

  public String toJson(ResponseContext rc) {

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("match", rc.getIsMatch());
    result.put("block", rc.getReason());

    Map<String, Object> map = Maps.newHashMap();

    map.put("id", rc.getRequestContext().getId());
    map.put("final_state", rc.finalState().name());

    map.put("@timestamp", zuluFormat.format(rc.getRequestContext().getTimestamp()));
    map.put("processingMillis", rc.getDurationMillis());

    map.put("error_type", rc.getError() != null ? rc.getError().getClass().getSimpleName() : null);
    map.put("error_message", rc.getError() != null ? rc.getError().getMessage() : null);
    //map.put("matched_block", rc.getResult() != null && rc.getResult().getBlock() != null ? rc.getResult().getBlock().getName() : null);

    RequestContext req = (RequestContext) rc.getRequestContext();

    map.put("content_len", req.getContentLength());
    map.put("content_len_kb", req.getContentLength() / 1024);
    map.put("type", req.getType());
    map.put("origin", req.getRemoteAddress());
    map.put("task_id", req.getTaskId());

    map.put("req_method", req.getMethod());
    map.put("headers", req.getHeaders().keySet());
    map.put("path", req.getUri());
    if (whitelistedHeaders.size() > 0) {
      Map<String, String> filteredHeardes = req.getHeaders().entrySet().stream()
              .filter(entry -> whitelistedHeaders.contains(entry.getKey()))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
      map.put("whitelisted_headers", filteredHeardes);
    }

    map.put("user", req.getLoggedInUser().isPresent() ? req.getLoggedInUser().get().getId() : null);

    map.put("action", req.getAction());
    map.put("indices", req.involvesIndices() ? req.getIndices() : Collections.emptySet());
    map.put("acl_history", req.getHistory().toString());
    map.put("body", req.getContent());

    // The cumbersome, awkward security handling in Java. (TM) #securityAgainstProductivity
    final String[] res = new String[1];

    AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
      try {
        res[0] = mapper.writeValueAsString(map);
      } catch (JsonProcessingException e) {
        throw new RuntimeException("JsonProcessingException", e);
      }
      return null;
    });

    // The stupidity of checked exceptions. (TM)

    return res[0];
  }

  public String toJson(RequestContext rc) {
    try {
      return mapper.writeValueAsString(rc);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

}
