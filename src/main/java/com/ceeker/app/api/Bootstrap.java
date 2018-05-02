package com.ceeker.app.api;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;

/**
 * @author vectorzhang
 * @desc 启动器
 * @date 2018/5/2 13:45
 */
@Slf4j
@Singleton
public class Bootstrap {
    public static void main(String[] args) {
        Injector injector = Guice.createInjector(new MyModule());
        AqiHandler aqiHandler = injector.getInstance(AqiHandler.class);
        aqiHandler.handleDataFiles();
    }
}

/**
 * 自定义module
 */
class MyModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();
    }
}
