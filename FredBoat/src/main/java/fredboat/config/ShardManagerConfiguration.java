/*
 * MIT License
 *
 * Copyright (c) 2017-2018 Frederik Ar. Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package fredboat.config;

import com.sedmelluq.discord.lavaplayer.jdaudp.NativeAudioSendFactory;
import fredboat.audio.player.LavalinkManager;
import fredboat.event.EventListenerBoat;
import fredboat.event.EventLogger;
import fredboat.feature.DikeSessionController;
import fredboat.feature.metrics.Metrics;
import fredboat.metrics.OkHttpEventMetrics;
import fredboat.util.rest.Http;
import net.dv8tion.jda.bot.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.bot.sharding.ShardManager;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.utils.SessionController;
import net.dv8tion.jda.core.utils.SessionControllerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.security.auth.login.LoginException;

/**
 * Created by napster on 23.02.18.
 *
 * Configures a shard manager bean
 */
@Configuration
public class ShardManagerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ShardManagerConfiguration.class);

    @Bean
    public SessionController getSessionController(Credentials credentials) {
        return credentials.getDikeUrl().isEmpty()
                ? new SessionControllerAdapter()
                : new DikeSessionController();
    }

    @Bean
    public ShardManager buildShardManager(PropertyConfigProvider configProvider, EventListenerBoat mainEventListener,
                                          LavalinkManager lavalinkManager, SessionController sessionController) {

        DefaultShardManagerBuilder builder = new DefaultShardManagerBuilder()
                .setToken(configProvider.getCredentials().getBotToken())
                .setGame(Game.playing(configProvider.getAppConfig().getGame()))
                .setBulkDeleteSplittingEnabled(false)
                .setEnableShutdownHook(false)
                .setAudioEnabled(true)
                .setAutoReconnect(true)
                .setSessionController(sessionController)
                .setContextEnabled(false)
                .setHttpClientBuilder(Http.DEFAULT_BUILDER.newBuilder()
                        .eventListener(new OkHttpEventMetrics("jda", Metrics.httpEventCounter)))
                .addEventListeners(mainEventListener)
                .addEventListeners(Metrics.instance().jdaEventsMetricsListener)
                .setShardsTotal(configProvider.getCredentials().getRecommendedShardCount());

        try {
            builder.addEventListeners(new EventLogger(configProvider.getEventLoggerConfig()));
        } catch (Exception e) {
            log.error("Failed to create Eventlogger, events / guild stats will not be logged to discord via webhook", e);
        }

        if (lavalinkManager.isEnabled()) {
            builder.addEventListeners(lavalinkManager.getLavalink());
        }

        if (!System.getProperty("os.arch").equalsIgnoreCase("arm")
                && !System.getProperty("os.arch").equalsIgnoreCase("arm-linux")) {
            builder.setAudioSendFactory(new NativeAudioSendFactory(800));
        }

        try {
            return builder.build();
        } catch (LoginException e) {
            throw new RuntimeException("Failed to log in to Discord! Is your token invalid?", e);
        }
    }
}