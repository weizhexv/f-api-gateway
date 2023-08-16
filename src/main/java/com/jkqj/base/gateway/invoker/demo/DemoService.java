package com.jkqj.base.gateway.invoker.demo;

import com.jkqj.common.result.Result;

public interface DemoService {
    Result<String> nullary();

    Result<PingResponse> unaryArray(PingRequest[] requests);

    Result<PingResponse> unaryMap(PingRequest request);

    Result<String> unaryString(String json);

    Result<String> unaryLong(Long num);

    Result<String> unaryInt(int num);

    Result<String> unaryIntArray(int[] numbers);

    Result<String> nAry(String name, int age, boolean isMale, String[] hobbies);

    Result<String> nAryArray(int[] numbers, String[] hobbies);

}
