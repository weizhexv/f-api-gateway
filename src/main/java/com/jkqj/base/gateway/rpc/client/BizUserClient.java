//package com.jkqj.base.gateway.rpc.client;
//
//import com.jkqj.base.gateway.rpc.dto.BizUserDTO;
//import com.jkqj.common.result.Result;
//import feign.Headers;
//import feign.Param;
//import feign.RequestLine;
//
//@RpcClient("biz-user")
//@Headers("Accept: application/json")
//public interface BizUserClient {
//    @RequestLine("GET /rpc/admin/v1/biz-user?bid={bid}")
//    public Result<BizUserDTO> getBizUserInfo(@Param("bid") Long bid);
//}
