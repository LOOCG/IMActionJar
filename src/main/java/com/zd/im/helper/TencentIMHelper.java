package com.zd.im.helper;

import com.tls.sigcheck.tls_sigcheck;
import com.zd.im.entity.IMActionResponse;
import com.zd.im.entity.IMRequestAddress;
import com.zd.im.entity.TencentIMConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.zd.im.imReqEntity.User.User;
import com.zd.im.imReqEntity.message.Message;
import com.zd.im.util.HttpClientUtil;
import com.zd.im.util.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.omg.CORBA.SystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;


/**
 * @author : cw
 * @create : 2018 - 07 - 04
 * 业务执行
 */
public class TencentIMHelper {
    private static final Logger log = LoggerFactory.getLogger(TencentIMHelper.class);

    private TencentIMConfig config;
    private ObjectMapper objectMapper;

    /**
     * 封装请求参数
     */
    private Joiner.MapJoiner joiner = Joiner.on("&").withKeyValueSeparator("=");

    public TencentIMHelper(TencentIMConfig config,ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成usersig
     *
     * @param identifier
     * @return
     */
    public String genUsersig(String identifier) {
        tls_sigcheck tlsSigcheck = new tls_sigcheck();
        tlsSigcheck.loadJniLib(config.getJnisigcheckLibPath());
        int ret = tlsSigcheck.tls_gen_signature_ex2(config.getSdkAppid(), identifier, config.getPrivateKey());
        if (0 != ret) {
            log.error("ret: {}, errMsg:{}", ret, tlsSigcheck.getErrMsg());
        } else {
            String usersig = tlsSigcheck.getSig();
            log.debug("identifier '{}' take usersig is {}", identifier, usersig);
            return usersig;
        }
        return  null;
    }

    /**
     * 导入账号
     *
     * @param user
     */
    public void accountImport(User user) {
        String url = InitHelper.getInstance().imRequestAddress.getAccountAmport();
        String queryString = joiner.join(getDefaultParams());
        Map<String, Object> requestBody = ImmutableMap.of(
                "Identifier", user.getIdentifier(),
                "Nick",user.getNick(),
                "FaceUrl",user.getFaceUrl(),
                "Type",user.getType());
        IMActionResponse res = request(url + queryString, requestBody, IMActionResponse.class);
        if (!res.isSuccess()) {
            log.error("导入'{}'到腾讯云IM失败, response message is: {}", res);
        }
    }

    /**
     * 批量导入账号
     *
     * @param accounts 用户名，单个用户名长度不超过 32 字节，单次最多导入100个用户名
     */
    public void multiaccountImport(String... accounts) {
        String url = InitHelper.getInstance().imRequestAddress.getMultiaccountImport();
        String queryString = joiner.join(getDefaultParams());
        Map<String, Object> requestBody = ImmutableMap.of("Accounts", accounts);
        IMActionResponse res = request(url + queryString, requestBody, IMActionResponse.class);
        if (!res.isSuccess()) {
            log.error("批量导入'{}'到腾讯云IM失败, response message is: {}", res);
        }
    }

    /**
     * 单发单聊消息
     */
    public  void sendMsg(Message message){
        String url = InitHelper.getInstance().imRequestAddress.getSendMsg();
        String queryString = joiner.join(getDefaultParams());
        IMActionResponse res =  request(url + queryString,  message, IMActionResponse.class);
        if (!res.isSuccess()) {
            log.error("单聊消息发送失败, response message is: {}", res);
        }
    }

    /**
     * 推送
     */
   public void imPush(Message message){
       String url = InitHelper.getInstance().imRequestAddress.getImPush();
       String queryString = joiner.join(getDefaultParams());
       IMActionResponse res =  request(url + queryString,  message, IMActionResponse.class);
       if (!res.isSuccess()) {
           log.error("推送消息发送失败, response message is: {}", res);
       }
   }

    /**
     * 获取推送报告
     * @return
     */
    public IMActionResponse imGetPushReport(String... taskIds){
        String url = InitHelper.getInstance().imRequestAddress.getImGetPushReport();
        String queryString = joiner.join(getDefaultParams());
        Map<String, Object> requestBody = ImmutableMap.of("TaskIds", taskIds);
        IMActionResponse res = request(url + queryString, requestBody, IMActionResponse.class);
        if (!res.isSuccess()) {
            log.error("获取推送报告'{}'失败, response message is: {}", res);
        }
        return  res;
    }

    /**
     * 获取默认设置的im admin 账号的usersig
     *
     * @return
     */
    public String getIMAdminUsersig() {
        if (StringUtils.isEmpty(config.getDefaultImAdminAccount())) {
            log.error("TencentIMConfig.defaultImAdminAccount不存在");
        }
        return getIMAdminUsersig(config.getDefaultImAdminAccount());
    }

    /**
     * 返回默认的参数
     *
     * @return
     */
    private Map<String, String> getDefaultParams() {
        Map<String, String> pathParams = Maps.newHashMap();
        pathParams.put("usersig", getIMAdminUsersig());
        pathParams.put("identifier", config.getDefaultImAdminAccount());
        pathParams.put("sdkappid", config.getSdkAppid());
        pathParams.put("random", UUID.randomUUID().toString().replace("-", "").toLowerCase());
        pathParams.put("contenttype", "json");
        return pathParams;
    }

    private <T> T request(String url, Object params, Class<T> cls) {
        return toBean(requestInvoke(url, params), cls);
    }


    /**
     * http 请求service
     * @param url
     * @param params
     * @return
     */
    private String requestInvoke(String url, Object params) {
        String json = null;
        try {
            String obj2Str = JsonUtils.obj2Str(params);
            log.info("data",obj2Str );
            json = HttpClientUtil.sendJsonData(url, obj2Str);
            log.info("request url {}, the params is: {}", url, objectMapper.writeValueAsString(params));
            log.info("request result is {}",json );
        } catch (Exception e) {
            e.printStackTrace();
        }
        return  json;
    }

    private <T> T toBean(String json, Class<T> cls) {
        try {
            return objectMapper.readValue(json, cls);
        } catch (IOException e) {
            log.error("json:{} 转换类型失败: {} ", json, cls);
            log.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * 获取im admin 账号的usersig
     *
     * @param identifier
     * @return
     */
    public String getIMAdminUsersig(String identifier) {
        String usersig = _getIMAdminUsersig(identifier);
        if (StringUtils.isEmpty(usersig)) {
            usersig = genUsersig(identifier);
            //    cacheIMAdminUsersig(identifier, usersig);
        }
        return usersig;
    }

    /**
     * 缓存中取账号usersig
     * @param identifier
     * @return
     */
    private String _getIMAdminUsersig(String identifier) {
        //todo
        return null;
    }

}