package com.zbkj.crmeb.front.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.common.CommonPage;
import com.common.MyRecord;
import com.common.PageParamRequest;
import com.constants.*;
import com.exception.CrmebException;
import com.github.pagehelper.PageInfo;
import com.utils.*;
import com.zbkj.crmeb.finance.model.UserRecharge;
import com.zbkj.crmeb.finance.request.UserExtractRequest;
import com.zbkj.crmeb.finance.service.UserExtractService;
import com.zbkj.crmeb.finance.service.UserRechargeService;
import com.zbkj.crmeb.front.request.UserRechargeRequest;
import com.zbkj.crmeb.front.request.UserSpreadPeopleRequest;
import com.zbkj.crmeb.front.request.WxBindingPhoneRequest;
import com.zbkj.crmeb.front.response.*;
import com.zbkj.crmeb.front.service.LoginService;
import com.zbkj.crmeb.front.service.UserCenterService;
import com.zbkj.crmeb.front.vo.WxPayJsResultVo;
import com.zbkj.crmeb.marketing.model.StoreCoupon;
import com.zbkj.crmeb.marketing.model.StoreCouponUser;
import com.zbkj.crmeb.marketing.service.StoreCouponService;
import com.zbkj.crmeb.marketing.service.StoreCouponUserService;
import com.zbkj.crmeb.payment.wechat.WeChatPayService;
import com.zbkj.crmeb.store.model.StoreOrder;
import com.zbkj.crmeb.store.service.StoreOrderService;
import com.zbkj.crmeb.system.model.SystemUserLevel;
import com.zbkj.crmeb.system.service.SystemConfigService;
import com.zbkj.crmeb.system.service.SystemGroupDataService;
import com.zbkj.crmeb.system.service.SystemUserLevelService;
import com.zbkj.crmeb.system.vo.SystemGroupDataRechargeConfigVo;
import com.zbkj.crmeb.user.dao.UserDao;
import com.zbkj.crmeb.user.model.*;
import com.zbkj.crmeb.user.request.RegisterThirdUserRequest;
import com.zbkj.crmeb.user.service.*;
import com.zbkj.crmeb.wechat.response.WeChatAuthorizeLoginGetOpenIdResponse;
import com.zbkj.crmeb.wechat.response.WeChatAuthorizeLoginUserInfoResponse;
import com.zbkj.crmeb.wechat.response.WeChatProgramAuthorizeLoginGetOpenIdResponse;
import com.zbkj.crmeb.wechat.service.WeChatService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * ???????????? ???????????????
 *  +----------------------------------------------------------------------
 *  | CRMEB [ CRMEB???????????????????????????????????? ]
 *  +----------------------------------------------------------------------
 *  | Copyright (c) 2016~2020 https://www.crmeb.com All rights reserved.
 *  +----------------------------------------------------------------------
 *  | Licensed CRMEB????????????????????????????????????????????????CRMEB????????????
 *  +----------------------------------------------------------------------
 *  | Author: CRMEB Team <admin@crmeb.com>
 *  +----------------------------------------------------------------------
 */
@Service
public class UserCenterServiceImpl extends ServiceImpl<UserDao, User> implements UserCenterService {

    private final Logger logger = LoggerFactory.getLogger(UserCenterServiceImpl.class);

    @Autowired
    private UserService userService;

    @Autowired
    private UserBillService userBillService;

    @Autowired
    private UserExtractService userExtractService;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private SystemUserLevelService systemUserLevelService;

    @Autowired
    private SystemGroupDataService systemGroupDataService;

    @Autowired
    private StoreOrderService storeOrderService;

    @Autowired
    private UserRechargeService userRechargeService;

    @Autowired
    private UserTokenService userTokenService;

    @Autowired
    private WeChatService weChatService;

    @Autowired
    private UserBrokerageRecordService userBrokerageRecordService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private WeChatPayService weChatPayService;

    @Autowired
    private StoreCouponService storeCouponService;

    @Autowired
    private StoreCouponUserService storeCouponUserService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private LoginService loginService;

    @Autowired
    private UserIntegralRecordService userIntegralRecordService;


    /**
     * ??????????????????(??????????????? ?????????????????? ????????????)
     */
    @Override
    public UserCommissionResponse getCommission() {
        User user = userService.getInfoException();

        // ???????????????
        BigDecimal yesterdayIncomes = userBrokerageRecordService.getYesterdayIncomes(user.getUid());
        //?????????????????????
        BigDecimal totalMoney = userExtractService.getExtractTotalMoney(user.getUid());

        UserCommissionResponse userCommissionResponse = new UserCommissionResponse();
        userCommissionResponse.setLastDayCount(yesterdayIncomes);
        userCommissionResponse.setExtractCount(totalMoney);
        userCommissionResponse.setCommissionCount(user.getBrokeragePrice());
        return userCommissionResponse;
    }

    /**
     * ????????????/????????????
     * @author Mr.Zhang
     * @since 2020-06-08
     * @return BigDecimal
     */
    @Override
    public BigDecimal getSpreadCountByType(int type) {
        //????????????/????????????
        Integer userId = userService.getUserIdException();
        if(type == 3){
            BigDecimal count = userBillService.getSumBigDecimal(null, userId, Constants.USER_BILL_CATEGORY_MONEY, null, Constants.USER_BILL_TYPE_BROKERAGE);
            BigDecimal withdraw = userBillService.getSumBigDecimal(1, userId, Constants.USER_BILL_CATEGORY_MONEY, null, Constants.USER_BILL_TYPE_BROKERAGE); //??????
            return count.subtract(withdraw);
        }

        //????????????
        if(type == 4){
            return userExtractService.getWithdrawn(null,null);
        }

        return BigDecimal.ZERO;
    }

    /**
     * ????????????
     * @author Mr.Zhang
     * @since 2020-06-08
     * @return Boolean
     */
    @Override
    public Boolean extractCash(UserExtractRequest request) {
        switch (request.getExtractType()){
            case "weixin":
                if(StringUtils.isBlank(request.getWechat())){
                    throw new  CrmebException("?????????????????????");
                }
                request.setAlipayCode(null);
                request.setBankCode(null);
                request.setBankName(null);
                break;
            case "alipay":
                if(StringUtils.isBlank(request.getAlipayCode())){
                    throw new  CrmebException("???????????????????????????");
                }
                request.setWechat(null);
                request.setBankCode(null);
                request.setBankName(null);
                break;
            case "bank":
                if(StringUtils.isBlank(request.getBankName())){
                    throw new  CrmebException("????????????????????????");
                }
                if(StringUtils.isBlank(request.getBankCode())){
                    throw new  CrmebException("????????????????????????");
                }
                request.setWechat(null);
                request.setAlipayCode(null);
                break;
            default:
                throw new  CrmebException("?????????????????????");
        }
        return userExtractService.extractApply(request);
    }

    /**
     * ????????????/??????????????????
     * @author Mr.Zhang
     * @since 2020-06-08
     * @return UserExtractCashResponse
     */
    @Override
    public List<String> getExtractBank() {
        // ??????????????????
        String bank = systemConfigService.getValueByKeyException(Constants.CONFIG_BANK_LIST).replace("\r\n", "\n");
        List<String> bankArr = new ArrayList<>();
        if(bank.indexOf("\n") > 0){
            bankArr.addAll(Arrays.asList(bank.split("\n")));
        }else{
            bankArr.add(bank);
        }
        return bankArr;
    }

    /**
     * ??????????????????
     * @author Mr.Zhang
     * @since 2020-06-19
     * @return List<UserLevel>
     */
    @Override
    public List<SystemUserLevel> getUserLevelList() {
        return systemUserLevelService.getH5LevelList();
    }

    /**
     * ??????????????? ??????????????????????????????
     * @return List<UserSpreadPeopleItemResponse>
     */
    @Override
    public List<UserSpreadPeopleItemResponse> getSpreadPeopleList(UserSpreadPeopleRequest request, PageParamRequest pageParamRequest) {
        //??????????????????????????????????????????
        Integer userId = userService.getUserIdException();
        List<Integer> userIdList = new ArrayList<>();
        userIdList.add(userId);
        userIdList = userService.getSpreadPeopleIdList(userIdList); //????????????????????????id??????

        if (CollUtil.isEmpty(userIdList)) {//??????????????????????????????????????????
            return new ArrayList<>();
        }
        if (request.getGrade().equals(1)) {// ???????????????
            //?????????????????????
            List<Integer> secondSpreadIdList = userService.getSpreadPeopleIdList(userIdList);
            //???????????????
            userIdList.clear();
            userIdList.addAll(secondSpreadIdList);
        }
        return userService.getSpreadPeopleList(userIdList, request.getKeyword(), request.getSortKey(), request.getIsAsc(), pageParamRequest);
    }

    /**
     * ??????????????????
     * @author Mr.Zhang
     * @since 2020-06-10
     * @return UserRechargeResponse
     */
    @Override
    public UserRechargeResponse getRechargeConfig() {
        UserRechargeResponse userRechargeResponse = new UserRechargeResponse();
        userRechargeResponse.setRechargeQuota(systemGroupDataService.getListByGid(SysGroupDataConstants.GROUP_DATA_ID_RECHARGE_LIST, UserRechargeItemResponse.class));
        String rechargeAttention = systemConfigService.getValueByKey(Constants.CONFIG_RECHARGE_ATTENTION);
        List<String> rechargeAttentionList = new ArrayList<>();
        if(StringUtils.isNotBlank(rechargeAttention)){
            rechargeAttentionList = CrmebUtil.stringToArrayStrRegex(rechargeAttention, "\n");
        }
        userRechargeResponse.setRechargeAttention(rechargeAttentionList);
        return userRechargeResponse;
    }

    /**
     * ??????????????????
     * @author Mr.Zhang
     * @since 2020-06-10
     * @return UserBalanceResponse
     */
    @Override
    public UserBalanceResponse getUserBalance() {
        User info = userService.getInfo();
        BigDecimal recharge = userBillService.getSumBigDecimal(1, info.getUid(), Constants.USER_BILL_CATEGORY_MONEY, null, null);
        BigDecimal orderStatusSum = storeOrderService.getSumBigDecimal(info.getUid(), null);
        return new UserBalanceResponse(info.getNowMoney(), recharge, orderStatusSum);
    }

    /**
     * ????????????
     * @return UserSpreadOrderResponse;
     */
    @Override
    public UserSpreadOrderResponse getSpreadOrder(PageParamRequest pageParamRequest) {
        User user = userService.getInfo();
        if (ObjectUtil.isNull(user)) {
            throw new CrmebException("??????????????????");
        }
        UserSpreadOrderResponse spreadOrderResponse = new UserSpreadOrderResponse();
        // ????????????????????????
        Integer spreadCount = userBrokerageRecordService.getSpreadCountByUid(user.getUid());
        spreadOrderResponse.setCount(spreadCount.longValue());
        if (spreadCount.equals(0)) {
            return spreadOrderResponse;
        }

        // ?????????????????????????????????
        List<UserBrokerageRecord> recordList = userBrokerageRecordService.findSpreadListByUid(user.getUid(), pageParamRequest);
        // ???????????????????????????
        List<String> orderNoList = recordList.stream().map(UserBrokerageRecord::getLinkId).collect(Collectors.toList());
        Map<String, StoreOrder> orderMap = storeOrderService.getMapInOrderNo(orderNoList);
        // ???????????????????????????
        List<StoreOrder> storeOrderList = new ArrayList<>(orderMap.values());
        List<Integer> uidList = storeOrderList.stream().map(StoreOrder::getUid).distinct().collect(Collectors.toList());
        HashMap<Integer, User> userMap = userService.getMapListInUid(uidList);

        List<UserSpreadOrderItemResponse> userSpreadOrderItemResponseList = new ArrayList<>();
        List<String> monthList = CollUtil.newArrayList();
        recordList.forEach(record -> {
            UserSpreadOrderItemChildResponse userSpreadOrderItemChildResponse = new UserSpreadOrderItemChildResponse();
            userSpreadOrderItemChildResponse.setOrderId(record.getLinkId());
            userSpreadOrderItemChildResponse.setTime(record.getUpdateTime());
            userSpreadOrderItemChildResponse.setNumber(record.getPrice());
            Integer orderUid = orderMap.get(record.getLinkId()).getUid();
            userSpreadOrderItemChildResponse.setAvatar(userMap.get(orderUid).getAvatar());
            userSpreadOrderItemChildResponse.setNickname(userMap.get(orderUid).getNickname());
            userSpreadOrderItemChildResponse.setType("??????");

            String month = DateUtil.dateToStr(record.getUpdateTime(), Constants.DATE_FORMAT_MONTH);
            if (monthList.contains(month)) {
                //????????????????????????????????????????????????????????????
                for (UserSpreadOrderItemResponse userSpreadOrderItemResponse : userSpreadOrderItemResponseList) {
                    if(userSpreadOrderItemResponse.getTime().equals(month)){
                        userSpreadOrderItemResponse.getChild().add(userSpreadOrderItemChildResponse);
                        break;
                    }
                }
            } else {// ??????????????????
                //????????????
                UserSpreadOrderItemResponse userSpreadOrderItemResponse = new UserSpreadOrderItemResponse();
                userSpreadOrderItemResponse.setTime(month);
                userSpreadOrderItemResponse.getChild().add(userSpreadOrderItemChildResponse);
                userSpreadOrderItemResponseList.add(userSpreadOrderItemResponse);
                monthList.add(month);
            }
        });

        // ????????????????????????
        Map<String, Integer> countMap = userBrokerageRecordService.getSpreadCountByUidAndMonth(user.getUid(), monthList);
        for (UserSpreadOrderItemResponse userSpreadOrderItemResponse: userSpreadOrderItemResponseList) {
            userSpreadOrderItemResponse.setCount(countMap.get(userSpreadOrderItemResponse.getTime()));
        }

        spreadOrderResponse.setList(userSpreadOrderItemResponseList);
        return spreadOrderResponse;
    }

    /**
     * ??????
     * @author Mr.Zhang
     * @since 2020-06-10
     * @return UserSpreadOrderResponse;
     */
    @Override
    @Transactional(rollbackFor = {RuntimeException.class, Error.class, CrmebException.class})
    public OrderPayResultResponse recharge(UserRechargeRequest request) {
        request.setPayType(Constants.PAY_TYPE_WE_CHAT);

        //?????????????????????????????????
        String rechargeMinAmountStr = systemConfigService.getValueByKeyException(Constants.CONFIG_KEY_RECHARGE_MIN_AMOUNT);
        BigDecimal rechargeMinAmount = new BigDecimal(rechargeMinAmountStr);
        int compareResult = rechargeMinAmount.compareTo(request.getPrice());
        if(compareResult > 0){
            throw new CrmebException("????????????????????????" + rechargeMinAmountStr);
        }

        request.setGivePrice(BigDecimal.ZERO);

        if(request.getGroupDataId() > 0){
            SystemGroupDataRechargeConfigVo systemGroupData = systemGroupDataService.getNormalInfo(request.getGroupDataId(), SystemGroupDataRechargeConfigVo.class);
            if(null == systemGroupData){
                throw new CrmebException("?????????????????????????????????");
            }

            //???????????????
            request.setPrice(systemGroupData.getPrice());
            request.setGivePrice(systemGroupData.getGiveMoney());

        }
        User currentUser = userService.getInfoException();
        //??????????????????

        UserRecharge userRecharge = new UserRecharge();
        userRecharge.setUid(currentUser.getUid());
        userRecharge.setOrderId(CrmebUtil.getOrderNo("recharge"));
        userRecharge.setPrice(request.getPrice());
        userRecharge.setGivePrice(request.getGivePrice());
        userRecharge.setRechargeType(request.getFromType());
        boolean save = userRechargeService.save(userRecharge);
        if (!save) {
            throw new CrmebException("????????????????????????!");
        }

        OrderPayResultResponse response = new OrderPayResultResponse();
        MyRecord record = new MyRecord();
        Map<String, String> unifiedorder = weChatPayService.unifiedRecharge(userRecharge, request.getClientIp());
        record.set("status", true);
        response.setStatus(true);
        WxPayJsResultVo vo = new WxPayJsResultVo();
        vo.setAppId(unifiedorder.get("appId"));
        vo.setNonceStr(unifiedorder.get("nonceStr"));
        vo.setPackages(unifiedorder.get("package"));
        vo.setSignType(unifiedorder.get("signType"));
        vo.setTimeStamp(unifiedorder.get("timeStamp"));
        vo.setPaySign(unifiedorder.get("paySign"));
        if (userRecharge.getRechargeType().equals(PayConstants.PAY_CHANNEL_WE_CHAT_H5)) {
            vo.setMwebUrl(unifiedorder.get("mweb_url"));
            response.setPayType(PayConstants.PAY_CHANNEL_WE_CHAT_H5);
        }
        if (userRecharge.getRechargeType().equals(PayConstants.PAY_CHANNEL_WE_CHAT_APP_IOS) || userRecharge.getRechargeType().equals(PayConstants.PAY_CHANNEL_WE_CHAT_APP_ANDROID)) {//
            vo.setPartnerid(unifiedorder.get("partnerid"));
        }
        response.setJsConfig(vo);
        response.setOrderNo(userRecharge.getOrderId());
        return response;
    }

    /**
     * ????????????
     * @author Mr.Zhang
     * @since 2020-06-10
     * @return UserSpreadOrderResponse;
     */
    @Override
    public LoginResponse weChatAuthorizeLogin(String code, Integer spreadUid) {
        // ??????code?????????????????????????????????
        WeChatAuthorizeLoginGetOpenIdResponse response = weChatService.authorizeLogin(code);
        //??????????????????
        UserToken userToken = userTokenService.getByOpenidAndType(response.getOpenId(),  Constants.THIRD_LOGIN_TOKEN_TYPE_PUBLIC);
        LoginResponse loginResponse = new LoginResponse();
        if (ObjectUtil.isNotNull(userToken)) {// ????????????????????????
            User user = userService.getById(userToken.getUid());
            if (!user.getStatus()) {
                throw new CrmebException("?????????????????????????????????????????????");
            }

            // ??????????????????????????????
            user.setLastLoginTime(DateUtil.nowDateTime());
            Boolean execute = transactionTemplate.execute(e -> {
                // ????????????
                if (userService.checkBingSpread(user, spreadUid, "old")) {
                    user.setSpreadUid(spreadUid);
                    user.setSpreadTime(DateUtil.nowDateTime());
                    // ???????????????????????????
                    userService.updateSpreadCountByUid(spreadUid, "add");
                }
                userService.updateById(user);
                return Boolean.TRUE;
            });
            if (!execute) {
                logger.error(StrUtil.format("??????????????????????????????????????????uid={},spreadUid={}", user.getUid(), spreadUid));
            }
            try {
                String token = userService.token(user);
                loginResponse.setToken(token);
            } catch (Exception e) {
                logger.error(StrUtil.format("?????????????????????token?????????uid={}", user.getUid()));
                e.printStackTrace();
            }
            loginResponse.setType("login");
            loginResponse.setUid(user.getUid());
            loginResponse.setNikeName(user.getNickname());
            loginResponse.setPhone(user.getPhone());
            return loginResponse;
        }
        // ????????????????????????????????????
        // ????????????????????????????????????Redis?????????key??????????????????????????????????????????????????????????????????
//        WeChatAuthorizeLoginUserInfoResponse userInfo = weChatService.getUserInfo(response.getOpenId(), response.getAccessToken());
        WeChatAuthorizeLoginUserInfoResponse userInfo = weChatService.getUserInfo(response.getAccessToken(), response.getOpenId());
        RegisterThirdUserRequest registerThirdUserRequest = new RegisterThirdUserRequest();
        BeanUtils.copyProperties(userInfo, registerThirdUserRequest);
        registerThirdUserRequest.setSpreadPid(spreadUid);
        registerThirdUserRequest.setType(Constants.USER_LOGIN_TYPE_PUBLIC);
        registerThirdUserRequest.setOpenId(response.getOpenId());
        String key = SecureUtil.md5(response.getOpenId());
        redisUtil.set(key, JSONObject.toJSONString(registerThirdUserRequest), (long) (60 * 2), TimeUnit.MINUTES);

        loginResponse.setType("register");
        loginResponse.setKey(key);
        return loginResponse;
    }

    /**
     * ??????????????????logo
     * @author Mr.Zhang
     * @since 2020-06-29
     * @return String;
     */
    @Override
    public String getLogo() {
        return systemConfigService.getValueByKey(Constants.CONFIG_KEY_MOBILE_LOGIN_LOGO);
    }

    /**
     * ???????????????
     * @param code String ??????????????????code
     * @param request RegisterThirdUserRequest ????????????
     * @return LoginResponse
     */
    @Override
    public LoginResponse weChatAuthorizeProgramLogin(String code, RegisterThirdUserRequest request) {
        WeChatProgramAuthorizeLoginGetOpenIdResponse response = weChatService.programAuthorizeLogin(code);
        System.out.println("????????????????????? = " + JSON.toJSONString(response));

        //??????????????????
        UserToken userToken = userTokenService.getByOpenidAndType(response.getOpenId(), Constants.THIRD_LOGIN_TOKEN_TYPE_PROGRAM);
        LoginResponse loginResponse = new LoginResponse();
        if(ObjectUtil.isNotNull(userToken)) {// ????????????????????????
            User user = userService.getById(userToken.getUid());
            if (!user.getStatus()) {
                throw new CrmebException("?????????????????????????????????????????????");
            }
            // ??????????????????????????????
            user.setLastLoginTime(DateUtil.nowDateTime());
            Boolean execute = transactionTemplate.execute(e -> {
                // ????????????
                if (userService.checkBingSpread(user, request.getSpreadPid(), "old")) {
                    user.setSpreadUid(request.getSpreadPid());
                    user.setSpreadTime(DateUtil.nowDateTime());
                    // ???????????????????????????
                    userService.updateSpreadCountByUid(request.getSpreadPid(), "add");
                }
                userService.updateById(user);
                return Boolean.TRUE;
            });
            if (!execute) {
                logger.error(StrUtil.format("??????????????????????????????????????????uid={},spreadUid={}", user.getUid(), request.getSpreadPid()));
            }

            try {
                String token = userService.token(user);
                loginResponse.setToken(token);
            } catch (Exception e) {
                logger.error(StrUtil.format("?????????????????????token?????????uid={}", user.getUid()));
                e.printStackTrace();
            }
            loginResponse.setType("login");
            loginResponse.setUid(user.getUid());
            loginResponse.setNikeName(user.getNickname());
            loginResponse.setPhone(user.getPhone());
            return loginResponse;
        }

        if (StrUtil.isBlank(request.getNickName()) && StrUtil.isBlank(request.getAvatar()) && StrUtil.isBlank(request.getHeadimgurl())) {
            // ???????????????????????????????????????
            loginResponse.setType("start");
            return loginResponse;
        }

        request.setType(Constants.USER_LOGIN_TYPE_PROGRAM);
        request.setOpenId(response.getOpenId());
        String key = SecureUtil.md5(response.getOpenId());
        redisUtil.set(key, JSONObject.toJSONString(request), (long) (60 * 2), TimeUnit.MINUTES);
        loginResponse.setType("register");
        loginResponse.setKey(key);
        return loginResponse;
    }

    /**
     * ??????????????????
     * @param type  String ????????????(week-??????month-???)
     * @param pageParamRequest PageParamRequest ??????
     * @return List<LoginResponse>
     */
    @Override
    public List<User> getTopSpreadPeopleListByDate(String type, PageParamRequest pageParamRequest) {
        return userService.getTopSpreadPeopleListByDate(type, pageParamRequest);
    }

    /**
     * ???????????????
     * @param type  String ????????????
     * @param pageParamRequest PageParamRequest ??????
     * @return List<User>
     */
    @Override
    public List<User> getTopBrokerageListByDate(String type, PageParamRequest pageParamRequest) {
        // ????????????????????????????????????
        List<UserBrokerageRecord> recordList = userBrokerageRecordService.getBrokerageTopByDate(type, pageParamRequest);
        if (CollUtil.isEmpty(recordList)) {
            return null;
        }

        List<Integer> uidList = recordList.stream().map(UserBrokerageRecord::getUid).collect(Collectors.toList());
        //????????????
        HashMap<Integer, User> userVoList = userService.getMapListInUid(uidList);

        //??????????????????
        List<User> userList = CollUtil.newArrayList();
        for (UserBrokerageRecord record: recordList) {
            User user = new User();
            User userVo = userVoList.get(record.getUid());

            user.setUid(record.getUid());
            user.setAvatar(userVo.getAvatar());
            user.setBrokeragePrice(record.getPrice());
            if(StrUtil.isBlank(userVo.getNickname())){
                user.setNickname(userVo.getPhone().substring(0, 2) + "****" + userVo.getPhone().substring(7));
            }else{
                user.setNickname(userVo.getNickname());
            }
            userList.add(user);
        }
        return userList;
    }

    /**
     * ???????????????
     * @author Mr.Zhang
     * @since 2020-06-10
     * @return List<SystemGroupData>
     */
    @Override
    public List<UserSpreadBannerResponse> getSpreadBannerList(PageParamRequest pageParamRequest) {
        return systemGroupDataService.getListByGid(Constants.GROUP_DATA_ID_SPREAD_BANNER_LIST, UserSpreadBannerResponse.class);
    }

    /**
     * ????????????????????????????????????
     * @param type  String ????????????
     * @return ???????????????
     */
    @Override
    public Integer getNumberByTop(String type) {
        int number = 0;
        Integer userId = userService.getUserIdException();
        PageParamRequest pageParamRequest = new PageParamRequest();
        pageParamRequest.setLimit(100);

        List<UserBrokerageRecord> recordList = userBrokerageRecordService.getBrokerageTopByDate(type, pageParamRequest);
        if (CollUtil.isEmpty(recordList)) {
            return number;
        }

        for (int i = 0; i < recordList.size(); i++) {
            if (recordList.get(i).getUid().equals(userId)) {
                number = i + 1;
                break ;
            }
        }
        return number;
    }

    /**
     * ??????????????????
     * @author Mr.Zhang
     * @since 2020-05-18
     * @return Boolean
     */
    @Override
    public Boolean transferIn(BigDecimal price) {
        //?????????????????????
        User user = userService.getInfo();
        if (ObjectUtil.isNull(user)) {
            throw new CrmebException("??????????????????");
        }
        BigDecimal subtract = user.getBrokeragePrice();

        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CrmebException("?????????????????????0");
        }
        if(subtract.compareTo(price) < 0){
            throw new CrmebException("??????????????????????????? " + subtract + "???");
        }
        // userBill??????????????????
        UserBill userBill = new UserBill();
        userBill.setUid(user.getUid());
        userBill.setLinkId("0");
        userBill.setPm(1);
        userBill.setTitle("???????????????");
        userBill.setCategory(Constants.USER_BILL_CATEGORY_MONEY);
        userBill.setType(Constants.USER_BILL_TYPE_TRANSFER_IN);
        userBill.setNumber(price);
        userBill.setBalance(user.getNowMoney().add(price));
        userBill.setMark(StrUtil.format("???????????????,??????{}", price));
        userBill.setStatus(1);
        userBill.setCreateTime(DateUtil.nowDateTime());

        // userBrokerage????????????
        UserBrokerageRecord brokerageRecord = new UserBrokerageRecord();
        brokerageRecord.setUid(user.getUid());
        brokerageRecord.setLinkId("0");
        brokerageRecord.setLinkType(BrokerageRecordConstants.BROKERAGE_RECORD_LINK_TYPE_YUE);
        brokerageRecord.setType(BrokerageRecordConstants.BROKERAGE_RECORD_TYPE_SUB);
        brokerageRecord.setTitle(BrokerageRecordConstants.BROKERAGE_RECORD_TITLE_BROKERAGE_YUE);
        brokerageRecord.setPrice(price);
        brokerageRecord.setBalance(user.getNowMoney().add(price));
        brokerageRecord.setMark(StrUtil.format("????????????????????????{}", price));
        brokerageRecord.setStatus(BrokerageRecordConstants.BROKERAGE_RECORD_STATUS_COMPLETE);
        brokerageRecord.setCreateTime(DateUtil.nowDateTime());

        Boolean execute = transactionTemplate.execute(e -> {
            // ?????????
            userService.operationBrokerage(user.getUid(), price, user.getBrokeragePrice(), "sub");
            // ?????????
            userService.operationNowMoney(user.getUid(), price, user.getNowMoney(), "add");
            userBillService.save(userBill);
            userBrokerageRecordService.save(brokerageRecord);
            return Boolean.TRUE;
        });
        return execute;
    }

    /**
     * ????????????
     * @author HZE
     * @since 2020-10-27
     */
    @Override
    public PageInfo<UserExtractRecordResponse> getExtractRecord(PageParamRequest pageParamRequest) {
        Integer userId = userService.getUserIdException();
        return userExtractService.getExtractRecord(userId, pageParamRequest);
    }

    /**
     * ??????????????????
     * @param pageParamRequest ????????????
     */
    @Override
    public PageInfo<SpreadCommissionDetailResponse> getSpreadCommissionDetail(PageParamRequest pageParamRequest) {
        User user = userService.getInfoException();
        return userBrokerageRecordService.findDetailListByUid(user.getUid(), pageParamRequest);
    }

    /**
     * ??????????????????????????????
     * @param type ???????????????all-?????????expenditure-?????????income-??????
     * @return CommonPage
     */
    @Override
    public CommonPage<UserRechargeBillRecordResponse> nowMoneyBillRecord(String type, PageParamRequest pageRequest) {
        User user = userService.getInfo();
        if (ObjectUtil.isNull(user)) {
            throw new CrmebException("??????????????????");
        }
        PageInfo<UserBill> billPageInfo = userBillService.nowMoneyBillRecord(user.getUid(), type, pageRequest);
        List<UserBill> list = billPageInfo.getList();

        // ?????????-???
        Map<String, List<UserBill>> map = CollUtil.newHashMap();
        list.forEach(i -> {
            String month = StrUtil.subPre(DateUtil.dateToStr(i.getCreateTime(), Constants.DATE_FORMAT), 7);
            if (map.containsKey(month)) {
                map.get(month).add(i);
            } else {
                List<UserBill> billList = CollUtil.newArrayList();
                billList.add(i);
                map.put(month, billList);
            }
        });
        List<UserRechargeBillRecordResponse> responseList = CollUtil.newArrayList();
        map.forEach((key, value) -> {
            UserRechargeBillRecordResponse response = new UserRechargeBillRecordResponse();
            response.setDate(key);
            response.setList(value);
            responseList.add(response);
        });

        PageInfo<UserRechargeBillRecordResponse> pageInfo = CommonPage.copyPageInfo(billPageInfo, responseList);
        return CommonPage.restPage(pageInfo);
    }

    /**
     * ???????????????????????????
     * @param request ????????????
     * @return ????????????
     */
    @Override
    public LoginResponse registerBindingPhone(WxBindingPhoneRequest request) {
        checkBindingPhone(request);

        // ???????????????????????????????????????
        Object o = redisUtil.get(request.getKey());
        if (ObjectUtil.isNull(o)) {
            throw new CrmebException("???????????????????????????????????????????????????");
        }
        RegisterThirdUserRequest registerThirdUserRequest = JSONObject.parseObject(o.toString(), RegisterThirdUserRequest.class);

        boolean isNew = true;

        User user = userService.getByPhone(request.getPhone());
        if (ObjectUtil.isNull(user)) {
            user = userService.registerByThird(registerThirdUserRequest);
            user.setPhone(request.getPhone());
            user.setAccount(request.getPhone());
            user.setSpreadUid(0);
            user.setPwd(CommonUtil.createPwd(request.getPhone()));
        } else {// ?????????????????????????????????????????????
            // ????????????????????????token
            int type = 0;
            switch (request.getType()) {
                case "public":
                    type = Constants.THIRD_LOGIN_TOKEN_TYPE_PUBLIC;
                    break;
                case "routine":
                    type = Constants.THIRD_LOGIN_TOKEN_TYPE_PROGRAM;
                    break;
            }

            UserToken userToken = userTokenService.getTokenByUserId(user.getUid(), type);
            if (ObjectUtil.isNotNull(userToken)) {
                throw new CrmebException("????????????????????????");
            }
            isNew = false;
        }

        User finalUser = user;
        boolean finalIsNew = isNew;
        Boolean execute = transactionTemplate.execute(e -> {
            if (finalIsNew) {// ?????????
                // ????????????
                if (userService.checkBingSpread(finalUser, registerThirdUserRequest.getSpreadPid(), "new")) {
                    finalUser.setSpreadUid(registerThirdUserRequest.getSpreadPid());
                    finalUser.setSpreadTime(DateUtil.nowDateTime());
                    userService.updateSpreadCountByUid(registerThirdUserRequest.getSpreadPid(), "add");
                }
                userService.save(finalUser);
                // ???????????????
                giveNewPeopleCoupon(finalUser.getUid());
            }
            switch (request.getType()) {
                case "public":
                    userTokenService.bind(registerThirdUserRequest.getOpenId(), Constants.THIRD_LOGIN_TOKEN_TYPE_PUBLIC, finalUser.getUid());
                    break;
                case "routine":
                    userTokenService.bind(registerThirdUserRequest.getOpenId(), Constants.THIRD_LOGIN_TOKEN_TYPE_PROGRAM, finalUser.getUid());
                    break;
            }
            return Boolean.TRUE;
        });
        if (!execute) {
            logger.error("?????????????????????????????????nickName = " + registerThirdUserRequest.getNickName());
        } else if (!isNew){// ????????????????????????
            if (ObjectUtil.isNotNull(registerThirdUserRequest.getSpreadPid()) && registerThirdUserRequest.getSpreadPid() > 0) {
                loginService.bindSpread(finalUser, registerThirdUserRequest.getSpreadPid());
            }
        }
        LoginResponse loginResponse = new LoginResponse();
        try {
            String token = userService.token(finalUser);
            loginResponse.setToken(token);
        } catch (Exception e) {
            logger.error(StrUtil.format("????????????????????????????????????token?????????uid={}", finalUser.getUid()));
            e.printStackTrace();
        }
        loginResponse.setType("login");
        loginResponse.setUid(user.getUid());
        loginResponse.setNikeName(user.getNickname());
        loginResponse.setPhone(user.getPhone());
        return loginResponse;
    }

    /**
     * ????????????????????????
     * @param pageParamRequest ????????????
     * @return List<UserIntegralRecord>
     */
    @Override
    public List<UserIntegralRecord> getUserIntegralRecordList(PageParamRequest pageParamRequest) {
        Integer uid = userService.getUserIdException();
        return userIntegralRecordService.findUserIntegralRecordList(uid, pageParamRequest);
    }

    /**
     * ????????????????????????
     * @return IntegralUserResponse
     */
    @Override
    public IntegralUserResponse getIntegralUser() {
        User user = userService.getInfoException();
        IntegralUserResponse userSignInfoResponse = new IntegralUserResponse();

        //??????
        Integer sumIntegral = userIntegralRecordService.getSumIntegral(user.getUid(), IntegralRecordConstants.INTEGRAL_RECORD_TYPE_ADD, "", null);
        Integer deductionIntegral = userIntegralRecordService.getSumIntegral(user.getUid(), IntegralRecordConstants.INTEGRAL_RECORD_TYPE_SUB, "", IntegralRecordConstants.INTEGRAL_RECORD_LINK_TYPE_ORDER);
        userSignInfoResponse.setSumIntegral(sumIntegral);
        userSignInfoResponse.setDeductionIntegral(deductionIntegral);
        // ????????????
        Integer frozenIntegral = userIntegralRecordService.getFrozenIntegralByUid(user.getUid());
        userSignInfoResponse.setFrozenIntegral(frozenIntegral);
        userSignInfoResponse.setIntegral(user.getIntegral());
        return userSignInfoResponse;
    }

    /**
     * ????????????????????????
     * @param pageParamRequest ????????????
     * @return List<UserBill>
     */
    @Override
    public List<UserBill> getUserExperienceList(PageParamRequest pageParamRequest) {
        Integer userId = userService.getUserIdException();
        return userBillService.getH5List(userId, Constants.USER_BILL_CATEGORY_EXPERIENCE, pageParamRequest);
    }

    /**
     * ??????????????????
     * @return UserExtractCashResponse
     */
    @Override
    public UserExtractCashResponse getExtractUser() {
        User user = userService.getInfoException();
        // ??????????????????
        String minPrice = systemConfigService.getValueByKeyException(SysConfigConstants.CONFIG_EXTRACT_MIN_PRICE);
        // ????????????
        String extractTime = systemConfigService.getValueByKey(SysConfigConstants.CONFIG_EXTRACT_FREEZING_TIME);
        // ???????????????
        BigDecimal brokeragePrice = user.getBrokeragePrice();
        // ????????????
        BigDecimal freeze = userBrokerageRecordService.getFreezePrice(user.getUid());
        return new UserExtractCashResponse(minPrice, brokeragePrice, freeze, extractTime);
    }

    /**
     * ?????????????????????
     * @return UserSpreadPeopleResponse
     */
    @Override
    public UserSpreadPeopleResponse getSpreadPeopleCount() {
        //??????????????????????????????????????????
        UserSpreadPeopleResponse userSpreadPeopleResponse = new UserSpreadPeopleResponse();
        List<Integer> userIdList = new ArrayList<>();
        Integer userId = userService.getUserIdException();
        userIdList.add(userId);
        userIdList = userService.getSpreadPeopleIdList(userIdList); //????????????????????????id??????

        if (CollUtil.isEmpty(userIdList)) {//??????????????????????????????????????????
            userSpreadPeopleResponse.setCount(0);
            userSpreadPeopleResponse.setTotal(0);
            userSpreadPeopleResponse.setTotalLevel(0);
            return userSpreadPeopleResponse;
        }

        userSpreadPeopleResponse.setTotal(userIdList.size()); //???????????????
        //?????????????????????
        List<Integer> secondSpreadIdList = userService.getSpreadPeopleIdList(userIdList);
        if (CollUtil.isEmpty(secondSpreadIdList)) {
            userSpreadPeopleResponse.setTotalLevel(0);
            userSpreadPeopleResponse.setCount(userSpreadPeopleResponse.getTotal());
            return userSpreadPeopleResponse;
        }
        userSpreadPeopleResponse.setTotalLevel(secondSpreadIdList.size());
        userSpreadPeopleResponse.setCount(userIdList.size() + secondSpreadIdList.size());
        return userSpreadPeopleResponse;
    }

    /**
     * ???????????????????????????
     */
    private void checkBindingPhone(WxBindingPhoneRequest request) {
        if (!request.getType().equals("public") && !request.getType().equals("routine")) {
            throw new CrmebException("?????????????????????");
        }
        if (request.getType().equals("public")) {
            if (StrUtil.isBlank(request.getCaptcha())) {
                throw new CrmebException("?????????????????????");
            }
            boolean matchPhone = ReUtil.isMatch(RegularConstants.PHONE, request.getPhone());
            if (!matchPhone) {
                throw new CrmebException("???????????????????????????????????????????????????");
            }
            // ??????????????????????????????
            boolean match = ReUtil.isMatch(RegularConstants.SMS_VALIDATE_CODE_NUM, request.getCaptcha());
            if (!match) {
                throw new CrmebException("??????????????????????????????????????????6?????????");
            }
            checkValidateCode(request.getPhone(), request.getCaptcha());
        } else {
            // ????????????
            if (StrUtil.isBlank(request.getCode())) {
                throw new CrmebException("????????????????????????code????????????");
            }
            if (StrUtil.isBlank(request.getEncryptedData())) {
                throw new CrmebException("????????????????????????????????????????????????");
            }
            if (StrUtil.isBlank(request.getIv())) {
                throw new CrmebException("???????????????????????????????????????????????????????????????");
            }
            // ??????appid
            String programAppId = systemConfigService.getValueByKey("routine_appid");
            if(StringUtils.isBlank(programAppId)){
                throw new CrmebException("???????????????appId?????????");
            }

            WeChatProgramAuthorizeLoginGetOpenIdResponse response = weChatService.programAuthorizeLogin(request.getCode());
            System.out.println("????????????????????? = " + JSON.toJSONString(response));
            String decrypt = WxUtil.decrypt(programAppId, request.getEncryptedData(), response.getSessionKey(), request.getIv());
            if (StrUtil.isBlank(decrypt)) {
                throw new CrmebException("??????????????????????????????????????????");
            }
            JSONObject jsonObject = JSONObject.parseObject(decrypt);
            if (StrUtil.isBlank(jsonObject.getString("phoneNumber"))) {
                throw new CrmebException("??????????????????????????????????????????????????????");
            }
            request.setPhone(jsonObject.getString("phoneNumber"));
        }
    }

    /**
     * ???????????????
     * @param uid ??????uid
     */
    private void giveNewPeopleCoupon(Integer uid) {
        // ??????????????????????????????????????????
        List<StoreCouponUser> couponUserList = CollUtil.newArrayList();
        List<StoreCoupon> couponList = storeCouponService.findRegisterList();
        if (CollUtil.isNotEmpty(couponList)) {
            couponList.forEach(storeCoupon -> {
                //??????????????????????????????
                if(!storeCoupon.getIsFixedTime()){
                    String endTime = DateUtil.addDay(DateUtil.nowDate(Constants.DATE_FORMAT), storeCoupon.getDay(), Constants.DATE_FORMAT);
                    storeCoupon.setUseEndTime(DateUtil.strToDate(endTime, Constants.DATE_FORMAT));
                    storeCoupon.setUseStartTime(DateUtil.nowDateTimeReturnDate(Constants.DATE_FORMAT));
                }

                StoreCouponUser storeCouponUser = new StoreCouponUser();
                storeCouponUser.setCouponId(storeCoupon.getId());
                storeCouponUser.setName(storeCoupon.getName());
                storeCouponUser.setMoney(storeCoupon.getMoney());
                storeCouponUser.setMinPrice(storeCoupon.getMinPrice());
                storeCouponUser.setUseType(storeCoupon.getUseType());
                if (storeCoupon.getIsFixedTime()) {// ??????????????????
                    storeCouponUser.setStartTime(storeCoupon.getUseStartTime());
                    storeCouponUser.setEndTime(storeCoupon.getUseEndTime());
                } else {// ????????????????????????
                    Date nowDate = DateUtil.nowDateTime();
                    storeCouponUser.setStartTime(nowDate);
                    DateTime dateTime = cn.hutool.core.date.DateUtil.offsetDay(nowDate, storeCoupon.getDay());
                    storeCouponUser.setEndTime(dateTime);
                }
                storeCouponUser.setType("register");
                if (storeCoupon.getUseType() > 1) {
                    storeCouponUser.setPrimaryKey(storeCoupon.getPrimaryKey());
                }
                storeCouponUser.setType(CouponConstants.STORE_COUPON_USER_TYPE_REGISTER);
                couponUserList.add(storeCouponUser);
            });
        }

        // ?????????????????????
        if (CollUtil.isNotEmpty(couponUserList)) {
            couponUserList.forEach(couponUser -> couponUser.setUid(uid));
            storeCouponUserService.saveBatch(couponUserList);
            couponList.forEach(coupon -> storeCouponService.deduction(coupon.getId(), 1, coupon.getIsLimited()));
        }
    }

    /**
     * ?????????????????????
     * @param phone ?????????
     * @param code ?????????
     */
    private void checkValidateCode(String phone, String code) {
        Object validateCode = redisUtil.get(SmsConstants.SMS_VALIDATE_PHONE + phone);
        if(validateCode == null){
            throw new CrmebException("??????????????????");
        }
        if(!validateCode.toString().equals(code)){
            throw new CrmebException("???????????????");
        }
        //???????????????
        redisUtil.remove(SmsConstants.SMS_VALIDATE_PHONE + phone);
    }
}
