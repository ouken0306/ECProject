package com.zbkj.crmeb.bargain.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.common.CommonPage;
import com.common.PageParamRequest;
import com.constants.BargainConstants;
import com.constants.Constants;
import com.exception.CrmebException;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.utils.CrmebUtil;
import com.utils.DateUtil;
import com.utils.vo.dateLimitUtilVo;
import com.zbkj.crmeb.bargain.dao.StoreBargainUserDao;
import com.zbkj.crmeb.bargain.model.StoreBargain;
import com.zbkj.crmeb.bargain.model.StoreBargainUser;
import com.zbkj.crmeb.bargain.model.StoreBargainUserHelp;
import com.zbkj.crmeb.bargain.request.StoreBargainUserSearchRequest;
import com.zbkj.crmeb.bargain.response.StoreBargainUserResponse;
import com.zbkj.crmeb.bargain.service.StoreBargainService;
import com.zbkj.crmeb.bargain.service.StoreBargainUserHelpService;
import com.zbkj.crmeb.bargain.service.StoreBargainUserService;
import com.zbkj.crmeb.front.request.BargainFrontRequest;
import com.zbkj.crmeb.front.response.BargainRecordResponse;
import com.zbkj.crmeb.front.response.BargainUserInfoResponse;
import com.zbkj.crmeb.store.model.StoreOrder;
import com.zbkj.crmeb.store.service.StoreOrderService;
import com.zbkj.crmeb.user.model.User;
import com.zbkj.crmeb.user.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * StoreBargainUserService ?????????
 * +----------------------------------------------------------------------
 * | CRMEB [ CRMEB???????????????????????????????????? ]
 * +----------------------------------------------------------------------
 * | Copyright (c) 2016~2020 https://www.crmeb.com All rights reserved.
 * +----------------------------------------------------------------------
 * | Licensed CRMEB????????????????????????????????????????????????CRMEB????????????
 * +----------------------------------------------------------------------
 * | Author: CRMEB Team <admin@crmeb.com>
 * +----------------------------------------------------------------------
 */
@Service
public class StoreBargainUserServiceImpl extends ServiceImpl<StoreBargainUserDao, StoreBargainUser> implements StoreBargainUserService {

    @Resource
    private StoreBargainUserDao dao;

    @Autowired
    private UserService userService;

    @Autowired
    private StoreBargainService storeBargainService;

    @Autowired
    private StoreBargainUserHelpService storeBargainUserHelpService;

    @Autowired
    private StoreOrderService storeOrderService;


    /**
    * ????????????????????????????????????
    * @param request ????????????
    * @param pageParamRequest ???????????????
    * @return List<StoreBargainUser>
    */
    @Override
    public PageInfo<StoreBargainUserResponse> getList(StoreBargainUserSearchRequest request, PageParamRequest pageParamRequest) {
        Page<StoreBargainUser> startPage = PageHelper.startPage(pageParamRequest.getPage(), pageParamRequest.getLimit());
        LambdaQueryWrapper<StoreBargainUser> lqw = new LambdaQueryWrapper<>();
        if (ObjectUtil.isNotNull(request.getStatus())) {
            lqw.eq(StoreBargainUser::getStatus, request.getStatus());
        }
        if (StrUtil.isNotBlank(request.getDateLimit())) {
            dateLimitUtilVo dateLimit = DateUtil.getDateLimit(request.getDateLimit());
            lqw.between(StoreBargainUser::getAddTime, DateUtil.dateStr2Timestamp(dateLimit.getStartTime(), Constants.DATE_TIME_TYPE_BEGIN), DateUtil.dateStr2Timestamp(dateLimit.getEndTime(), Constants.DATE_TIME_TYPE_END));
        }
        lqw.orderByDesc(StoreBargainUser::getId);
        List<StoreBargainUser> bargainUserList = dao.selectList(lqw);
        if (CollUtil.isEmpty(bargainUserList)) {
            return CommonPage.copyPageInfo(startPage, CollUtil.newArrayList());
        }
        List<StoreBargainUserResponse> list = bargainUserList.stream().map(bargainUser -> {
            StoreBargainUserResponse bargainUserResponse = new StoreBargainUserResponse();
            BeanUtils.copyProperties(bargainUser, bargainUserResponse);
            bargainUserResponse.setAddTime(DateUtil.timestamp2DateStr(bargainUser.getAddTime(), Constants.DATE_FORMAT));
            bargainUserResponse.setNowPrice(bargainUser.getBargainPrice().subtract(bargainUser.getPrice()));
            // ??????????????????
            User user = userService.getById(bargainUser.getUid());
            bargainUserResponse.setAvatar(user.getAvatar());
            bargainUserResponse.setNickname(user.getNickname());
            // ????????????????????????
            StoreBargain storeBargain = storeBargainService.getById(bargainUser.getBargainId());
            bargainUserResponse.setTitle(storeBargain.getTitle());

            bargainUserResponse.setDataTime(DateUtil.timestamp2DateStr(storeBargain.getStopTime(), Constants.DATE_FORMAT));
            bargainUserResponse.setPeopleNum(storeBargain.getPeopleNum());
            // ??????????????????
            Long helpCount = storeBargainUserHelpService.getHelpCountByBargainIdAndBargainUid(storeBargain.getId(), bargainUser.getId());
            bargainUserResponse.setNum(storeBargain.getPeopleNum() - helpCount.intValue());
            return bargainUserResponse;
        }).collect(Collectors.toList());

        return CommonPage.copyPageInfo(startPage, list);
    }

    /**
     * ????????????????????????
     * @param bargainId ????????????ID
     * @return List<StoreBargainUser>
     */
    @Override
    public List<StoreBargainUser> getListByBargainId(Integer bargainId) {
        QueryWrapper<StoreBargainUser> qw = new QueryWrapper<>();
        qw.select("id", "status");
        qw.eq("bargain_id", bargainId).eq("is_del", false);
        return dao.selectList(qw);
    }

    /**
     * ??????????????????ID + ??????uid ??????????????????????????????
     * @param bargainId ??????????????????
     * @param uid       ????????????uid
     * @return StoreBargainUser
     */
    @Override
    public StoreBargainUser getByBargainIdAndUid(Integer bargainId, Integer uid) {
        LambdaQueryWrapper<StoreBargainUser> lqw = new LambdaQueryWrapper<>();
        lqw.eq(StoreBargainUser::getBargainId, bargainId);
        lqw.eq(StoreBargainUser::getUid, uid);
        lqw.eq(StoreBargainUser::getIsDel, false);
        lqw.orderByDesc(StoreBargainUser::getId);
        List<StoreBargainUser> userList = dao.selectList(lqw);
        if (CollUtil.isEmpty(userList)) {
            return null;
        }
        return userList.get(0);
    }

    /**
     * ??????????????????ID + ??????uid ???????????????????????????????????????
     * @param bargainId ??????????????????
     * @param uid       ????????????uid
     * @return StoreBargainUser
     */
    @Override
    public StoreBargainUser getByBargainIdAndUidAndPink(Integer bargainId, Integer uid) {
        LambdaQueryWrapper<StoreBargainUser> lqw = new LambdaQueryWrapper<>();
        lqw.eq(StoreBargainUser::getBargainId, bargainId);
        lqw.eq(StoreBargainUser::getUid, uid);
        lqw.eq(StoreBargainUser::getIsDel, false);
        lqw.eq(StoreBargainUser::getStatus, BargainConstants.BARGAIN_USER_STATUS_PARTICIPATE);
        lqw.orderByDesc(StoreBargainUser::getId);
        List<StoreBargainUser> userList = dao.selectList(lqw);
        if (CollUtil.isEmpty(userList)) {
            return null;
        }
        return userList.get(0);
    }

    /**
     * ??????????????????ID + ??????uid ???????????????????????????????????????
     * @param bargainId ??????????????????
     * @param uid       ????????????uid
     * @return StoreBargainUser
     */
    @Override
    public List<StoreBargainUser> getListByBargainIdAndUid(Integer bargainId, Integer uid) {
        LambdaQueryWrapper<StoreBargainUser> lqw = new LambdaQueryWrapper<>();
        lqw.eq(StoreBargainUser::getBargainId, bargainId);
        lqw.eq(StoreBargainUser::getUid, uid);
        lqw.eq(StoreBargainUser::getIsDel, false);
        return dao.selectList(lqw);
    }

    /**
     * ????????????????????????????????????
     * @param bargainUser ????????????
     * @return List<StoreBargainUser>
     */
    @Override
    public List<StoreBargainUser> getByEntity(StoreBargainUser bargainUser) {
        LambdaQueryWrapper<StoreBargainUser> lqw = Wrappers.lambdaQuery();
        lqw.setEntity(bargainUser);
        return dao.selectList(lqw);
    }

    /**
     * ????????????????????????Header
     */
    @Override
    public List<StoreBargainUser> getHeaderList() {
        LambdaQueryWrapper<StoreBargainUser> lqw = Wrappers.lambdaQuery();
        lqw.eq(StoreBargainUser::getStatus, 3);
        lqw.eq(StoreBargainUser::getIsDel, false);
        lqw.groupBy(StoreBargainUser::getUid);
        lqw.orderByDesc(StoreBargainUser::getId);
        lqw.last(" limit 10");
        return dao.selectList(lqw);
    }

    /**
     * ????????????????????????
     * @param bargainFrontRequest ????????????
     * @return BargainUserInfoResponse
     */
    @Override
    public BargainUserInfoResponse getBargainUserInfo(BargainFrontRequest bargainFrontRequest) {
        if (ObjectUtil.isNull(bargainFrontRequest.getBargainUserId()) || bargainFrontRequest.getBargainUserId()<= 0) { // ???????????????????????????
            return oneselfBargainActivity(bargainFrontRequest);
        }
        return otherBargainActivity(bargainFrontRequest);
    }

    /**
     * ??????????????????????????????
     * @param bargainFrontRequest ????????????
     * @return BargainUserInfoResponse
     */
    private BargainUserInfoResponse otherBargainActivity(BargainFrontRequest bargainFrontRequest) {
        User user = userService.getInfoException();
        // ????????????????????????
        StoreBargain storeBargain = storeBargainService.getById(bargainFrontRequest.getBargainId());
        if (ObjectUtil.isNull(storeBargain) || storeBargain.getIsDel()) {
            throw new CrmebException("?????????????????????????????????");
        }
        if (!storeBargain.getStatus()) {
            throw new CrmebException("?????????????????????");
        }
        if (storeBargain.getStock() <= 0 || storeBargain.getQuota() <= 0) {
            throw new CrmebException("?????????????????????");
        }
        long currentTimeMillis = System.currentTimeMillis();
        if (storeBargain.getStopTime() < currentTimeMillis) {
            throw new CrmebException("???????????????");
        }
        StoreBargainUser bargainUser = getById(bargainFrontRequest.getBargainUserId());
        if (ObjectUtil.isNull(bargainUser)) {
            throw new CrmebException("???????????????????????????");
        }
        if (bargainUser.getIsDel()) {
            throw new CrmebException("???????????????????????????");
        }
        if (bargainUser.getStatus().equals(2)) {
            throw new CrmebException("?????????????????????");
        }
        // ????????????????????????????????????
        BargainUserInfoResponse infoResponse = new BargainUserInfoResponse();
        int bargainStatus;// ????????????
        int percent;// ???????????????
        if (bargainUser.getUid().equals(user.getUid())) {// ?????????????????????
            if (bargainUser.getStatus().equals(3)) {// ???????????????
                // ????????????????????????
                StoreOrder bargainOrder = storeOrderService.getByBargainOrder(bargainUser.getBargainId(), bargainUser.getId());
                if (ObjectUtil.isNotNull(bargainOrder)) {// ?????????
                    // ??????????????????
                    if (!bargainOrder.getPaid()) {// ?????????
                        bargainStatus = 8;// ??????????????????????????????
                        BigDecimal alreadyPrice = bargainUser.getPrice();// ????????????
                        BigDecimal surplusPrice = BigDecimal.ZERO;// ????????????
                        percent = 100;
                        infoResponse.setBargainStatus(bargainStatus);
                        infoResponse.setAlreadyPrice(alreadyPrice);
                        infoResponse.setSurplusPrice(surplusPrice);
                        infoResponse.setBargainPercent(percent);
                        // ????????????????????????
                        infoResponse.setUserHelpList(getHelpList(bargainUser.getId()));
                        infoResponse.setStoreBargainUserId(bargainUser.getId());
                        return infoResponse;
                    }
                    // ?????????
                    bargainStatus = 9;// ?????????????????????
                    BigDecimal alreadyPrice = bargainUser.getPrice();// ????????????
                    BigDecimal surplusPrice = BigDecimal.ZERO;// ????????????
                    percent = 100;
                    infoResponse.setBargainStatus(bargainStatus);
                    infoResponse.setAlreadyPrice(alreadyPrice);
                    infoResponse.setSurplusPrice(surplusPrice);
                    infoResponse.setBargainPercent(percent);
                    // ????????????????????????
                    infoResponse.setUserHelpList(getHelpList(bargainUser.getId()));
                    infoResponse.setStoreBargainUserId(bargainUser.getId());
                    return infoResponse;
                }
                // ?????????
                bargainStatus = 4;// ???????????????
                BigDecimal alreadyPrice = bargainUser.getPrice();// ????????????
                BigDecimal surplusPrice = BigDecimal.ZERO;// ????????????
                percent = 100;
                infoResponse.setBargainStatus(bargainStatus);
                infoResponse.setAlreadyPrice(alreadyPrice);
                infoResponse.setSurplusPrice(surplusPrice);
                infoResponse.setBargainPercent(percent);
                // ????????????????????????
                infoResponse.setUserHelpList(getHelpList(bargainUser.getId()));
                infoResponse.setStoreBargainUserId(bargainUser.getId());
                return infoResponse;
            }
            bargainStatus = 3;// ?????????
            BigDecimal alreadyPrice = bargainUser.getPrice();// ????????????
            BigDecimal surplusPrice = bargainUser.getBargainPrice().subtract(storeBargain.getMinPrice()).subtract(alreadyPrice);// ????????????
            percent =  CrmebUtil.percentInstanceIntVal(alreadyPrice, alreadyPrice.add(surplusPrice));
            infoResponse.setBargainStatus(bargainStatus);
            infoResponse.setAlreadyPrice(alreadyPrice);
            infoResponse.setSurplusPrice(surplusPrice);
            infoResponse.setBargainPercent(percent);
            // ????????????????????????
            infoResponse.setUserHelpList(getHelpList(bargainUser.getId()));
            infoResponse.setStoreBargainUserId(bargainUser.getId());
            return infoResponse;
        }
        // ????????????????????????
        if (bargainUser.getStatus().equals(3)) {
            bargainStatus = 4;// ???????????????
            BigDecimal alreadyPrice = bargainUser.getPrice();// ????????????
            BigDecimal surplusPrice = BigDecimal.ZERO;// ????????????
            percent = 100;
            infoResponse.setBargainStatus(bargainStatus);
            infoResponse.setAlreadyPrice(alreadyPrice);
            infoResponse.setSurplusPrice(surplusPrice);
            infoResponse.setBargainPercent(percent);
            // ????????????????????????
            infoResponse.setUserHelpList(getHelpList(bargainUser.getId()));
            infoResponse.setStoreBargainUserId(bargainUser.getId());
            User tempUser = userService.getById(bargainUser.getUid());
            infoResponse.setStoreBargainUserName(tempUser.getNickname());
            infoResponse.setStoreBargainUserAvatar(tempUser.getAvatar());
            return infoResponse;
        }
        // ?????????ta??????
        Boolean isHelp = storeBargainUserHelpService.getIsHelp(bargainUser.getId(), user.getUid());
        if (isHelp) { // ?????????
            bargainStatus = 6;// ?????????
            BigDecimal alreadyPrice = bargainUser.getPrice();// ????????????
            BigDecimal surplusPrice = bargainUser.getBargainPrice().subtract(storeBargain.getMinPrice()).subtract(alreadyPrice);// ????????????
            percent = CrmebUtil.percentInstanceIntVal(alreadyPrice, alreadyPrice.add(surplusPrice));
            infoResponse.setBargainStatus(bargainStatus);
            infoResponse.setAlreadyPrice(alreadyPrice);
            infoResponse.setSurplusPrice(surplusPrice);
            infoResponse.setBargainPercent(percent);
            // ????????????????????????
            infoResponse.setUserHelpList(getHelpList(bargainUser.getId()));
            infoResponse.setStoreBargainUserId(bargainUser.getId());
            User tempUser = userService.getById(bargainUser.getUid());
            infoResponse.setStoreBargainUserName(tempUser.getNickname());
            infoResponse.setStoreBargainUserAvatar(tempUser.getAvatar());
            return infoResponse;
        }
        // ??????????????????????????????????????????
        Integer helpNum = getHelpNumByBargainIdAndUid(bargainFrontRequest.getBargainId(), user.getUid());
        if (storeBargain.getBargainNum() <= helpNum) {
            bargainStatus = 7;// ??????????????????
            BigDecimal alreadyPrice = bargainUser.getPrice();// ????????????
            BigDecimal surplusPrice = bargainUser.getBargainPrice().subtract(storeBargain.getMinPrice()).subtract(alreadyPrice);// ????????????
            percent = CrmebUtil.percentInstanceIntVal(alreadyPrice, alreadyPrice.add(surplusPrice));
            infoResponse.setBargainStatus(bargainStatus);
            infoResponse.setAlreadyPrice(alreadyPrice);
            infoResponse.setSurplusPrice(surplusPrice);
            infoResponse.setBargainPercent(percent);
            // ????????????????????????
            infoResponse.setUserHelpList(getHelpList(bargainUser.getId()));
            infoResponse.setStoreBargainUserId(bargainUser.getId());
            User tempUser = userService.getById(bargainUser.getUid());
            infoResponse.setStoreBargainUserName(tempUser.getNickname());
            infoResponse.setStoreBargainUserAvatar(tempUser.getAvatar());
            return infoResponse;
        }
        // ???????????????
        bargainStatus = 5;// ????????????
        BigDecimal alreadyPrice = bargainUser.getPrice();// ????????????
        BigDecimal surplusPrice = bargainUser.getBargainPrice().subtract(storeBargain.getMinPrice()).subtract(alreadyPrice);// ????????????
        percent = CrmebUtil.percentInstanceIntVal(alreadyPrice, alreadyPrice.add(surplusPrice));
        infoResponse.setBargainStatus(bargainStatus);
        infoResponse.setAlreadyPrice(alreadyPrice);
        infoResponse.setSurplusPrice(surplusPrice);
        infoResponse.setBargainPercent(percent);
        // ????????????????????????
        infoResponse.setUserHelpList(getHelpList(bargainUser.getId()));
        infoResponse.setStoreBargainUserId(bargainUser.getId());
        User tempUser = userService.getById(bargainUser.getUid());
        infoResponse.setStoreBargainUserName(tempUser.getNickname());
        infoResponse.setStoreBargainUserAvatar(tempUser.getAvatar());
        return infoResponse;
    }

    /**
     * ????????????????????????
     * @param bargainUserId ????????????id
     * @return List<StoreBargainUserHelp>
     */
    private List<StoreBargainUserHelp> getHelpList(Integer bargainUserId) {
        List<StoreBargainUserHelp> helpList = storeBargainUserHelpService.getHelpListByBargainUserId(bargainUserId);
        helpList.forEach(e -> {
            User helpUser = userService.getById(e.getUid());
            e.setNickname(helpUser.getNickname());
            e.setAvatar(helpUser.getAvatar());
            e.setAddTimeStr(cn.hutool.core.date.DateUtil.date(e.getAddTime()).toString());
        });
        return helpList;
    }

    /**
     * ?????????????????????
     * @param bargainFrontRequest ????????????
     * @return BargainUserInfoResponse
     */
    private BargainUserInfoResponse oneselfBargainActivity(BargainFrontRequest bargainFrontRequest) {
        User user = userService.getInfoException();
        // ????????????????????????
        StoreBargain storeBargain = storeBargainService.getById(bargainFrontRequest.getBargainId());
        if (ObjectUtil.isNull(storeBargain) || storeBargain.getIsDel()) {
            throw new CrmebException("?????????????????????????????????");
        }
        if (!storeBargain.getStatus()) {
            throw new CrmebException("?????????????????????");
        }
        if (storeBargain.getStock() <= 0 || storeBargain.getQuota() <= 0) {
            throw new CrmebException("?????????????????????");
        }
        long currentTimeMillis = System.currentTimeMillis();
        if (storeBargain.getStopTime() < currentTimeMillis) {
            throw new CrmebException("???????????????");
        }

        BargainUserInfoResponse infoResponse = new BargainUserInfoResponse();
        // ????????????????????????????????????
        StoreBargainUser storeBargainUser = getLastByIdAndUid(bargainFrontRequest.getBargainId(), user.getUid());
        int percent = 0;// ???????????????
        int bargainStatus = 1;// ???????????????1-??????????????????
        if (ObjectUtil.isNull(storeBargainUser)) {// ????????????????????????????????????
            BigDecimal alreadyPrice = BigDecimal.ZERO;// ????????????
            BigDecimal surplusPrice = storeBargain.getPrice().subtract(storeBargain.getMinPrice());// ????????????
            infoResponse.setBargainStatus(bargainStatus);
            infoResponse.setAlreadyPrice(alreadyPrice);
            infoResponse.setSurplusPrice(surplusPrice);
            infoResponse.setBargainPercent(percent);
            return infoResponse;
        }
        if (storeBargainUser.getStatus().equals(2)) {// ??????????????????????????????
            throw new CrmebException("??????????????????????????????");
        }
        // ???????????????????????????
        if (storeBargainUser.getIsDel()) { // ?????????
            // ??????????????????????????????
            Integer bargainCount = getCountByBargainIdAndUid(bargainFrontRequest.getBargainId(), user.getUid());
            if (storeBargain.getNum() >= bargainCount) {
                bargainStatus = 2;// ??????????????????
                BigDecimal alreadyPrice = BigDecimal.ZERO;// ????????????
                BigDecimal surplusPrice = storeBargain.getPrice().subtract(storeBargain.getMinPrice());// ????????????
                infoResponse.setBargainStatus(bargainStatus);
                infoResponse.setAlreadyPrice(alreadyPrice);
                infoResponse.setSurplusPrice(surplusPrice);
                return infoResponse;
            }
            // ???????????????
            BigDecimal alreadyPrice = BigDecimal.ZERO;// ????????????
            BigDecimal surplusPrice = storeBargain.getPrice().subtract(storeBargain.getMinPrice());// ????????????
            infoResponse.setBargainStatus(bargainStatus);
            infoResponse.setAlreadyPrice(alreadyPrice);
            infoResponse.setSurplusPrice(surplusPrice);
            infoResponse.setBargainPercent(percent);
            return infoResponse;
        }
        if (storeBargainUser.getStatus().equals(3)) {// ???????????????
            // ????????????????????????
            StoreOrder bargainOrder = storeOrderService.getByBargainOrder(storeBargainUser.getBargainId(), storeBargainUser.getId());
            if (ObjectUtil.isNotNull(bargainOrder)) {// ?????????
                // ??????????????????
                if (!bargainOrder.getPaid()) {// ?????????
                    bargainStatus = 8;// ??????????????????????????????
                    BigDecimal alreadyPrice = storeBargainUser.getPrice();// ????????????
                    BigDecimal surplusPrice = BigDecimal.ZERO;// ????????????
                    percent = 100;
                    infoResponse.setBargainStatus(bargainStatus);
                    infoResponse.setAlreadyPrice(alreadyPrice);
                    infoResponse.setSurplusPrice(surplusPrice);
                    infoResponse.setBargainPercent(percent);
                    // ????????????????????????
                    infoResponse.setUserHelpList(getHelpList(storeBargainUser.getId()));
                    infoResponse.setStoreBargainUserId(storeBargainUser.getId());
                    return infoResponse;
                }
                // ??????????????????????????????
                // ??????????????????????????????
                Integer bargainCount = getCountByBargainIdAndUid(bargainFrontRequest.getBargainId(), user.getUid());
                if (storeBargain.getNum() <= bargainCount) {
                    bargainStatus = 2;// ??????????????????
                    BigDecimal alreadyPrice = BigDecimal.ZERO;// ????????????
                    BigDecimal surplusPrice = storeBargain.getPrice().subtract(storeBargain.getMinPrice());// ????????????
                    infoResponse.setBargainStatus(bargainStatus);
                    infoResponse.setAlreadyPrice(alreadyPrice);
                    infoResponse.setSurplusPrice(surplusPrice);
                    return infoResponse;
                }
                // ???????????????
                BigDecimal alreadyPrice = BigDecimal.ZERO;// ????????????
                BigDecimal surplusPrice = storeBargain.getPrice().subtract(storeBargain.getMinPrice());// ????????????
                infoResponse.setBargainStatus(bargainStatus);
                infoResponse.setAlreadyPrice(alreadyPrice);
                infoResponse.setSurplusPrice(surplusPrice);
                infoResponse.setBargainPercent(percent);
                return infoResponse;
            }
            // ????????????
            bargainStatus = 4;// ???????????????
            BigDecimal alreadyPrice = storeBargainUser.getPrice();// ????????????
            BigDecimal surplusPrice = BigDecimal.ZERO;// ????????????
            percent = 100;
            infoResponse.setBargainStatus(bargainStatus);
            infoResponse.setAlreadyPrice(alreadyPrice);
            infoResponse.setSurplusPrice(surplusPrice);
            infoResponse.setBargainPercent(percent);
            // ????????????????????????
            infoResponse.setUserHelpList(getHelpList(storeBargainUser.getId()));
            infoResponse.setStoreBargainUserId(storeBargainUser.getId());
            return infoResponse;
        }
        // ????????????????????????
        bargainStatus = 3;// ?????????
        BigDecimal alreadyPrice = storeBargainUser.getPrice();// ????????????
        BigDecimal surplusPrice = storeBargainUser.getBargainPrice().subtract(storeBargain.getMinPrice()).subtract(alreadyPrice);// ????????????
        percent =  CrmebUtil.percentInstanceIntVal(alreadyPrice, alreadyPrice.add(surplusPrice));
        infoResponse.setBargainStatus(bargainStatus);
        infoResponse.setAlreadyPrice(alreadyPrice);
        infoResponse.setSurplusPrice(surplusPrice);
        infoResponse.setBargainPercent(percent);
        // ????????????????????????
        infoResponse.setUserHelpList(getHelpList(storeBargainUser.getId()));
        infoResponse.setStoreBargainUserId(storeBargainUser.getId());
        return infoResponse;
    }

    /**
     * ??????????????????????????????
     * @return StoreBargainUser
     */
    private StoreBargainUser getLastByIdAndUid(Integer id, Integer uid) {
        LambdaQueryWrapper<StoreBargainUser> lqw = Wrappers.lambdaQuery();
        lqw.eq(StoreBargainUser::getBargainId, id);
        lqw.eq(StoreBargainUser::getUid, uid);
        lqw.orderByDesc(StoreBargainUser::getId);
        lqw.last(" limit 1");
        return dao.selectOne(lqw);
    }

    /**
     * ????????????
     * @return PageInfo<BargainRecordResponse>
     */
    @Override
    public PageInfo<BargainRecordResponse> getRecordList(PageParamRequest pageParamRequest) {
        Integer userId = userService.getUserIdException();
        Page<Object> startPage = PageHelper.startPage(pageParamRequest.getPage(), pageParamRequest.getLimit());
        LambdaQueryWrapper<StoreBargainUser> lqw = Wrappers.lambdaQuery();
        lqw.eq(StoreBargainUser::getUid, userId);
        lqw.orderByDesc(StoreBargainUser::getId);
        List<StoreBargainUser> bargainUserList = dao.selectList(lqw);
        if (CollUtil.isEmpty(bargainUserList)) {
            return new PageInfo<>();
        }
        List<Integer> bargainIdList = bargainUserList.stream().map(StoreBargainUser::getBargainId).distinct().collect(Collectors.toList());
        HashMap<Integer, StoreBargain> bargainMap = storeBargainService.getMapInId(bargainIdList);
        List<BargainRecordResponse> responseList = bargainUserList.stream().map(e -> {
            BargainRecordResponse recordResponse = new BargainRecordResponse();
            StoreBargain storeBargain = bargainMap.get(e.getBargainId());
            BeanUtils.copyProperties(storeBargain, recordResponse);
            recordResponse.setBargainUserId(e.getId());
            recordResponse.setStatus(e.getStatus());
            recordResponse.setIsDel(e.getIsDel());
            recordResponse.setIsOrder(false);
            recordResponse.setIsPay(false);
            if (!e.getIsDel() && e.getStatus().equals(3)) {
                // ?????????????????????
                StoreOrder bargainOrder = storeOrderService.getByBargainOrder(e.getBargainId(), e.getId());
                if (ObjectUtil.isNotNull(bargainOrder)) {
                    recordResponse.setIsOrder(true);
                    if (bargainOrder.getIsDel()) {
                        recordResponse.setIsDel(true);
                    } else if (bargainOrder.getPaid()) {
                        recordResponse.setIsPay(true);
                    } else {
                        recordResponse.setOrderNo(bargainOrder.getOrderId());
                    }
                }
            }
            // ????????????
            BigDecimal surplusPrice;
            if (e.getStatus().equals(3)) {
                surplusPrice = e.getBargainPriceMin();
            } else {
                surplusPrice = e.getBargainPriceMin().add(e.getBargainPrice()).subtract(e.getPrice());
            }
            recordResponse.setSurplusPrice(surplusPrice);
            return recordResponse;
        }).collect(Collectors.toList());

        return CommonPage.copyPageInfo(startPage, responseList);
    }

    private Integer getHelpNumByBargainIdAndUid(Integer bargainId, Integer uid) {
        // ????????????????????????????????????
        LambdaQueryWrapper<StoreBargainUser> lqw = Wrappers.lambdaQuery();
        lqw.select(StoreBargainUser::getId);
        lqw.eq(StoreBargainUser::getBargainId, bargainId);
        lqw.eq(StoreBargainUser::getUid, uid);
        List<StoreBargainUser> bargainUserList = dao.selectList(lqw);
        if (CollUtil.isEmpty(bargainUserList)) {
            return 0;
        }
        List<Integer> bargainUserIdList = bargainUserList.stream().map(StoreBargainUser::getId).collect(Collectors.toList());
        return storeBargainUserHelpService.getHelpCountByBargainIdAndUidInBUserId(bargainId, uid, bargainUserIdList);
    }

    /**
     * ???????????????????????????????????????????????????
     * @param bargainId ????????????id
     * @param uid ??????uid
     * @return ?????????????????????????????????????????????
     */
    private Integer getCountByBargainIdAndUid(Integer bargainId, Integer uid) {
        LambdaQueryWrapper<StoreBargainUser> lqw = Wrappers.lambdaQuery();
        lqw.eq(StoreBargainUser::getBargainId, bargainId);
        lqw.eq(StoreBargainUser::getUid, uid);
        lqw.eq(StoreBargainUser::getIsDel, false);
        lqw.eq(StoreBargainUser::getStatus, 3);
        return dao.selectCount(lqw);
    }

}

