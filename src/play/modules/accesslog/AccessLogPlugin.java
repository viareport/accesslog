/*
 * Copyright 2011 Brian Nesbitt
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package play.modules.accesslog;

import java.io.File;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.jboss.netty.handler.codec.http.HttpHeaders;

import play.Play;
import play.PlayPlugin;
import play.mvc.Http;
import play.mvc.Scope.Session;

public class AccessLogPlugin extends PlayPlugin
{
    // vhost remoteAddress - requestUser [time] "requestUrl" status bytes "referrer" "userAgent" requestTime
    // "POST"
    private static final String FORMAT = "%v %h - %u [%t] \"%r\" %s %b \"%ref\" \"%ua\" %rt";
    private static final String CONFIG_PREFIX = "accesslog.";
    private static final String DEFAULT_PATH = "logs/access.log";
    private static boolean _enabled;
    private boolean _shouldLog2Play;
    private boolean _shouldLogPost;
    private File _logFile;
    private static Logger ACCESS_LOGGER; 
    

    @Override
    public void onConfigurationRead()
    {
        _shouldLog2Play = Boolean.parseBoolean(Play.configuration.getProperty(CONFIG_PREFIX + "log2play",
            "false"));
        _shouldLogPost = Boolean.parseBoolean(Play.configuration.getProperty(CONFIG_PREFIX + "logpost",
            "false"));
        _logFile = new File(Play.configuration.getProperty(CONFIG_PREFIX + "path", DEFAULT_PATH));
        _enabled = Boolean.parseBoolean(Play.configuration.getProperty(CONFIG_PREFIX + "enabled",
            "true"));

        if (!_logFile.isAbsolute())
        {
            _logFile = new File(play.Play.applicationPath, _logFile.getPath());
        }
        
        
    }

    private static Logger configureLogger(File logFile) {
        Logger accessLogger = Logger.getLogger("AccessLogPlugin");
        if (! accessLogger.getAllAppenders().hasMoreElements()) {
            FileAppender fileAppdr = new FileAppender();
            fileAppdr.setFile(logFile.getPath());
            accessLogger.addAppender(fileAppdr);
        }
        return accessLogger;
    }

    @Override
    public void onApplicationStart()
    {
        ACCESS_LOGGER = configureLogger(_logFile);
    }

    @Override
    public void invocationFinally()
    {
        play.Logger.info("AccessLogPlugin log");
        log();
    }

    private synchronized void log()
    {
        if (!_shouldLog2Play && !_enabled)
        {
            return;
        }
        
        Http.Request request = Http.Request.current();
        Http.Response response = Http.Response.current();
        if (request == null || response == null)
        {
            return;
        }

        long requestProcessingTime = System.currentTimeMillis() - request.date.getTime();

        String bytes = "-";
        String status = "-";

        /*
         * It seems as though the Response.current() is only valid when the request is handled by a controller
         * Serving static files, static 404's and 500's etc don't populate the same Response.current()
         * This prevents us from getting the bytes sent and response status all of the time
         */
        if (request.action != null && response.out.size() > 0)
        {
            bytes = String.valueOf(response.out.size());
            status = response.status.toString();
        }

        String line = getInfoLine(request, getUserName(request), status, bytes,
            getReferrer(request), getUserAgent(request), String.valueOf(requestProcessingTime));


        if (_shouldLogPost && request.method.equals("POST")) {
            line = appendPostPayLoad(request, line);
        }

        line = StringUtils.trim(line);

        if (_enabled)
        {
            ACCESS_LOGGER.info(line);
        }

        if (_shouldLog2Play)
        {
            play.Logger.info(line);
        }
    }

    private String appendPostPayLoad(Http.Request request, String line) {
        String body = request.params.get("body");

        if (StringUtils.isNotEmpty(body))
        {
            line = line + " \""+ body + "\"";
        } else {
            line = line + " \"\"";
        }
        return line;
    }

    private String getInfoLine(Http.Request request, String userName, String status,
        String bytes, String referrer, String userAgent, String requestProcessingTime) {
        String line = FORMAT;
        line = StringUtils.replaceOnce(line, "%v", request.host);
        line = StringUtils.replaceOnce(line, "%h", request.remoteAddress);
        line = StringUtils.replaceOnce(line, "%u", userName);
        line = StringUtils.replaceOnce(line, "%t", request.date.toString());
        line = StringUtils.replaceOnce(line, "%r", request.url);
        line = StringUtils.replaceOnce(line, "%s", status);
        line = StringUtils.replaceOnce(line, "%b", bytes);
        line = StringUtils.replaceOnce(line, "%ref", referrer);
        line = StringUtils.replaceOnce(line, "%ua", userAgent);
        line = StringUtils.replaceOnce(line, "%rt", requestProcessingTime);
        return line;
    }

    private String getUserAgent(Http.Request request) {
        Http.Header userAgent = request.headers.get(HttpHeaders.Names.USER_AGENT.toLowerCase());
        return (userAgent != null) ? userAgent.value() : "";
    }

    private String getReferrer(Http.Request request) {
        Http.Header referrer = request.headers.get(HttpHeaders.Names.REFERER.toLowerCase());
        return (referrer != null) ? referrer.value() : "";
    }

    private String getUserName(Http.Request request) {
        String userName = request.user;
        if (StringUtils.isEmpty(userName)) {
            Session currentSession = Session.current();
            userName = currentSession == null ? null : currentSession.get("username");
        }
        return (StringUtils.isEmpty(userName)) ? "-" : userName;
    }
}