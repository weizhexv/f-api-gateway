//package com.jkqj.base.gateway.rpc.client;
//
//import feign.Feign;
//import feign.Logger;
//import feign.Request;
//import feign.Retryer;
//import feign.gson.GsonDecoder;
//import feign.gson.GsonEncoder;
//import feign.okhttp.OkHttpClient;
//import feign.slf4j.Slf4jLogger;
//import org.apache.logging.log4j.util.Strings;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.env.Environment;
//
//import static com.google.common.base.Preconditions.checkState;
//import static java.util.concurrent.TimeUnit.MILLISECONDS;
//
//@Configuration
//public class FeignConfig {
//    public static final String CLIENT_PREFIX = "jkqj.rpc.client";
//    public static final String DELIMITER = ".";
//
//    @Autowired
//    Environment env;
//
//    public <T> T build(Class<T> type) {
//        RpcClient annotation = type.getAnnotation(RpcClient.class);
//        checkState(annotation != null);
//        String name = annotation.value();
//        checkState(Strings.isNotBlank(name));
//
//        String target = env.getRequiredProperty(CLIENT_PREFIX + DELIMITER + name);
//        Request.Options options = new Request.Options(
//                5000, MILLISECONDS,
//                10000, MILLISECONDS,
//                true
//        );
//
//        return Feign.builder()
//                .client(new OkHttpClient())
//                .options(options)
//                .encoder(new GsonEncoder())
//                .decoder(new GsonDecoder())
//                .retryer(Retryer.NEVER_RETRY)
//                .logger(new Slf4jLogger(type))
//                .logLevel(Logger.Level.FULL)
//                .target(type, target);
//    }
//
//    @Bean
//    public BizUserClient bizUserClient() {
//        return build(BizUserClient.class);
//    }
//}
