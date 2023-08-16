package com.jkqj.base.gateway.invoker.demo;

import com.jkqj.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.rpc.RpcContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

@DubboService(interfaceClass = DemoService.class, version = "1.0.0", validation = "true")
@Slf4j
public class DemoServiceImpl implements DemoService {
    public Result<PingResponse> ping(String name, int age, boolean isMale, String[] hobbies) {
        log.debug("got request {}, {}, {}, {}", name, age, isMale, hobbies);
        log.debug("rpc attachment {}", RpcContext.getServiceContext().getObjectAttachments());

        var address = new PingResponse.Address("beijing", "China");
        return Result.success(new PingResponse(name, hobbies, LocalDateTime.now(), new Date(), LocalDate.now(), LocalTime.now(), address));
    }

    public Result<PingResponse> ping1(PingRequest request) {
        log.debug("got request {}", request);
        log.debug("rpc attachment {}", RpcContext.getServiceContext().getObjectAttachments());
        var address = new PingResponse.Address("beijing", "China");
        return Result.success(new PingResponse(request.getName(), request.getHobbies(), LocalDateTime.now(), new Date(), LocalDate.now(), LocalTime.now(), address));
    }

    public Result<String> ping2(String name) {
        log.debug("got name {}", name);
        return Result.success(name);
    }

    @Override
    public Result<String> nullary() {
        log.debug("enter nullary");
        return Result.success("nullary pass");
    }

    @Override
    public Result<PingResponse> unaryArray(PingRequest[] requests) {
        log.debug("got requests {}", (Object) requests);
        var request = requests[0];
        var address = new PingResponse.Address("beijing", "China");
        return Result.success(new PingResponse(request.getName(), request.getHobbies(), LocalDateTime.now(), new Date(), LocalDate.now(), LocalTime.now(), address));
    }

    @Override
    public Result<PingResponse> unaryMap(PingRequest request) {
        log.debug("got request {}", request);
        var address = new PingResponse.Address("beijing", "China");
        return Result.success(new PingResponse(request.getName(), request.getHobbies(), LocalDateTime.now(), new Date(), LocalDate.now(), LocalTime.now(), address));
    }

    @Override
    public Result<String> unaryString(String json) {
        log.debug("got json {}", json);
        return Result.success(json);
    }

    @Override
    public Result<String> unaryLong(Long num) {
        log.debug("got Long {}", num);
        return Result.success("passed " + num);
    }

    @Override
    public Result<String> unaryInt(int num) {
        log.debug("got int {}", num);
        return Result.success("passed " + num);
    }

    @Override
    public Result<String> unaryIntArray(int[] numbers) {
        log.debug("got int[] {}", numbers);
        return Result.success("pass");
    }

    @Override
    public Result<String> nAry(String name, int age, boolean isMale, String[] hobbies) {
        log.debug("got name {}, age {}, isMale {}, hobbies {}", name, age, isMale, hobbies);
        return Result.success("pass");
    }

    @Override
    public Result<String> nAryArray(int[] numbers, String[] hobbies) {
        log.debug("got numbers {}, hobbies {}", numbers, hobbies);
        return Result.success("passed");
    }
}
