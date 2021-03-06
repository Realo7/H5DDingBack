package com.dingtalk.controller;

import com.alibaba.fastjson.JSON;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiUserGetRequest;
import com.dingtalk.api.request.OapiUserGetuserinfoRequest;
import com.dingtalk.api.response.OapiUserGetResponse;
import com.dingtalk.api.response.OapiUserGetuserinfoResponse;
import com.dingtalk.domain.ConfigDTO;
import com.dingtalk.config.AppConfig;
import com.dingtalk.domain.ServiceResult;
import com.dingtalk.domain.UserDTO;
import com.dingtalk.exception.DingtalkEncryptException;
import com.dingtalk.service.TokenService;
import com.dingtalk.util.JsApiSignature;
import com.taobao.api.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.dingtalk.config.UrlConstant.URL_GET_USER_INFO;
import static com.dingtalk.config.UrlConstant.URL_USER_GET;

/**
 * 钉钉企业内部微应用DEMO, 实现了身份验证（免登）功能
 */
@RestController
@CrossOrigin("*") // NOTE：此处仅为本地调试使用，为避免安全风险，生产环境请勿设置CORS为 '*'
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private TokenService tokenService;
    private AppConfig appConfig;

    @Autowired
    public AuthController(
        TokenService tokenService,
        AppConfig appConfig
    ) {
        this.tokenService = tokenService;
        this.appConfig = appConfig;
    }

    /**
     * 欢迎页面，通过 /welcome 访问，判断后端服务是否启动
     *
     * @return 字符串 welcome
     */
    @GetMapping("/welcome")
    public String welcome() {
        return "welcome";
    }

    /**
     * 钉钉用户登录，显示当前登录用户的userId和名称
     *
     * @param authCode 免登临时authCode
     * @return 当前用户
     */
    @PostMapping("/login")
    public ServiceResult<UserDTO> login(@RequestParam String authCode) {
        System.out.println("2收到的参数"+authCode);
        String accessToken;

        // 获取accessToken
        ServiceResult<String> accessTokenSr = tokenService.getAccessToken();
        if (!accessTokenSr.isSuccess()) {
            return ServiceResult.failure(accessTokenSr.getCode(), accessTokenSr.getMessage());
        }
        accessToken = accessTokenSr.getResult();

        // 获取用户userId
        ServiceResult<String> userIdSr = getUserInfo(accessToken, authCode);
        if (!userIdSr.isSuccess()) {
            return ServiceResult.failure(userIdSr.getCode(), userIdSr.getMessage());
        }

        // 获取用户详情
        return getUser(accessToken, userIdSr.getResult());
    }

    /**
     * 访问/user/getuserinfo接口获取用户userId
     *
     * @param accessToken access_token
     * @param authCode    临时授权码
     * @return 用户userId或错误信息
     */
    private ServiceResult<String> getUserInfo(String accessToken, String authCode) {
        System.out.println("3收到的参数AccessToken:"+accessToken+"应用码"+authCode);
        DingTalkClient client = new DefaultDingTalkClient(URL_GET_USER_INFO);
        OapiUserGetuserinfoRequest request = new OapiUserGetuserinfoRequest();
        request.setCode(authCode);
        request.setHttpMethod("GET");

        OapiUserGetuserinfoResponse response;
        try {
            response = client.execute(request, accessToken);
        } catch (ApiException e) {
            log.error("Failed to {}", URL_GET_USER_INFO, e);
            return ServiceResult.failure(e.getErrCode(), "Failed to getUserInfo: " + e.getErrMsg());
        }
        if (!response.isSuccess()) {
            return ServiceResult.failure(response.getErrorCode(), response.getErrmsg());
        }
        System.out.println("3-2从钉钉获取的信息"+ JSON.toJSONString(response));
        return ServiceResult.success(response.getUserid());
    }

    /**
     * 访问/user/get 获取用户名称
     *
     * @param accessToken access_token
     * @param userId      用户userId
     * @return 用户名称或错误信息
     */
    @RequestMapping("/user/get")
    private ServiceResult<UserDTO> getUser(String accessToken, String userId) {
        System.out.println("4收到的参数"+accessToken+"用户编号"+userId);
        DingTalkClient client = new DefaultDingTalkClient(URL_USER_GET);
        OapiUserGetRequest request = new OapiUserGetRequest();
        request.setUserid(userId);
        request.setHttpMethod("GET");

        OapiUserGetResponse response;
        try {
            response = client.execute(request, accessToken);
        } catch (ApiException e) {
            log.error("Failed to {}", URL_USER_GET, e);
            return ServiceResult.failure(e.getErrCode(), "Failed to getUserName: " + e.getErrMsg());
        }
        System.out.println("4-2从钉钉获取的用户信息"+ JSON.toJSONString(response));

        UserDTO user = new UserDTO();
        user.setName(response.getName());
        user.setUserid(response.getUserid());
        user.setAvatar(response.getAvatar());
        user.setMobile(response.getMobile());
        user.setAccessToken(accessToken);

        return ServiceResult.success(user);
    }

    @PostMapping("/config")
    public ServiceResult<ConfigDTO> config(@RequestParam String url) {
        System.out.println("1收到的参数"+url);
        ConfigDTO config = new ConfigDTO();

        ServiceResult<String> jsTicketSr = tokenService.getJsTicket();
        if (!jsTicketSr.isSuccess()) {
            return ServiceResult.failure(jsTicketSr.getCode(), jsTicketSr.getMessage());
        }

        config.setAgentId(appConfig.getAgentId());
        config.setCorpId(appConfig.getCorpId());
        config.setJsticket(jsTicketSr.getResult());
        config.setNonceStr(JsApiSignature.genNonce());
        config.setTimeStamp(System.currentTimeMillis() / 1000);
        String sign;
        try {
            sign = JsApiSignature.sign(url, config.getNonceStr(), config.getTimeStamp(), config.getJsticket());
        } catch (DingtalkEncryptException e) {
            return ServiceResult.failure(e.getCode().toString(), e.getMessage());
        }
        config.setSignature(sign);
        System.out.println("返回的参数"+config);
        return ServiceResult.success(config);
    }
}
