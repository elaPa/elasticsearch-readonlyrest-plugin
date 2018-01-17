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

package tech.beshu.ror.acl.blocks.rules.impl;

import cz.seznam.euphoria.shaded.guava.com.google.common.cache.Cache;
import cz.seznam.euphoria.shaded.guava.com.google.common.cache.CacheBuilder;
import tech.beshu.ror.acl.blocks.rules.BasicAuthentication;
import tech.beshu.ror.commons.shims.es.ESContext;
import tech.beshu.ror.commons.shims.es.LoggerShim;
import tech.beshu.ror.settings.rules.AuthKeyUnixRuleSettings;
import tech.beshu.ror.utils.BasicAuthUtils.BasicAuth;

import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.codec.digest.Crypt.crypt;


public class AuthKeyUnixSyncRule extends BasicAuthentication {

  private final LoggerShim logger;
  private final AuthKeyUnixRuleSettings settings;
  private final Cache<AbstractMap.SimpleEntry<String, String>, String> cachedCrypt =
    CacheBuilder.newBuilder().maximumSize(5000).concurrencyLevel(Runtime.getRuntime().availableProcessors()).build();

  public AuthKeyUnixSyncRule(AuthKeyUnixRuleSettings s, ESContext context) {
    super(s, context);
    this.logger = context.logger(AuthKeyUnixSyncRule.class);
    this.settings = s;
  }

  @Override
  protected boolean authenticate(String configuredAuthKey, BasicAuth basicAuth) {
    try {
      String decodedProvided = new String(Base64.getDecoder().decode(basicAuth.getBase64Value()), StandardCharsets.UTF_8);
      decodedProvided = roundHash(configuredAuthKey.split(":"), decodedProvided.split(":"));
      return decodedProvided.equals(configuredAuthKey);
    } catch (Throwable e) {
      logger.warn("Exception while authentication", e);
      return false;
    }
  }

  private String roundHash(String[] key, String[] login) {
    Pattern p = Pattern.compile("((?:[^$]*\\$){3}[^$]*).*");
    Matcher m = p.matcher(key[1]);
    String result = "";
    if (m.find()) {
      AbstractMap.SimpleEntry<String, String> cryptArgs = new AbstractMap.SimpleEntry<>(login[1], m.group(1));
      String cryptRes = cachedCrypt.getIfPresent(cryptArgs);
      if (cryptRes == null) {
        cryptRes = crypt(cryptArgs.getKey(), cryptArgs.getValue());
        cachedCrypt.put(cryptArgs, cryptRes);
      }
      result = login[0] + ":" + cryptRes;
    }
    return result;
  }

  @Override
  public String getKey() {
    return settings.getName();
  }
}