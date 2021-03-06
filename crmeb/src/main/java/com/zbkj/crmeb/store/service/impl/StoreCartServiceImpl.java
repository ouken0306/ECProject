package com.zbkj.crmeb.store.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.common.CommonPage;
import com.common.MyRecord;
import com.common.PageParamRequest;
import com.constants.Constants;
import com.exception.CrmebException;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.zbkj.crmeb.bargain.model.StoreBargain;
import com.zbkj.crmeb.combination.model.StoreCombination;
import com.zbkj.crmeb.front.request.CartNumRequest;
import com.zbkj.crmeb.front.request.CartResetRequest;
import com.zbkj.crmeb.front.response.CartInfoResponse;
import com.zbkj.crmeb.seckill.model.StoreSeckill;
import com.zbkj.crmeb.store.dao.StoreCartDao;
import com.zbkj.crmeb.store.model.StoreCart;
import com.zbkj.crmeb.store.model.StoreProduct;
import com.zbkj.crmeb.store.model.StoreProductAttrValue;
import com.zbkj.crmeb.store.response.StoreCartResponse;
import com.zbkj.crmeb.store.response.StoreProductCartProductInfoResponse;
import com.zbkj.crmeb.store.response.StoreProductResponse;
import com.zbkj.crmeb.store.service.StoreCartService;
import com.zbkj.crmeb.store.service.StoreProductAttrValueService;
import com.zbkj.crmeb.store.service.StoreProductService;
import com.zbkj.crmeb.store.utilService.OrderUtils;
import com.zbkj.crmeb.system.service.SystemConfigService;
import com.zbkj.crmeb.user.model.User;
import com.zbkj.crmeb.user.model.UserLevel;
import com.zbkj.crmeb.user.service.UserLevelService;
import com.zbkj.crmeb.user.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * StoreCartServiceImpl ????????????
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
public class StoreCartServiceImpl extends ServiceImpl<StoreCartDao, StoreCart> implements StoreCartService {

    @Resource
    private StoreCartDao dao;

    @Autowired
    private StoreProductService storeProductService;

    @Autowired
    private UserService userService;

    @Autowired
    private StoreProductAttrValueService storeProductAttrValueService;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private UserLevelService userLevelService;

    @Autowired
    private OrderUtils orderUtils;

    /**
    * ??????
    * @param pageParamRequest ???????????????
    * @param isValid ????????????
    * @return List<CartInfoResponse>
    */
    @Override
    public PageInfo<CartInfoResponse> getList(PageParamRequest pageParamRequest, boolean isValid) {
        Integer userId = userService.getUserIdException();
        Page<StoreCart> page = PageHelper.startPage(pageParamRequest.getPage(), pageParamRequest.getLimit());
        //??? StoreCart ?????????????????????
        LambdaQueryWrapper<StoreCart> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(StoreCart::getUid, userId);
        lambdaQueryWrapper.eq(StoreCart::getStatus, isValid);
        lambdaQueryWrapper.eq(StoreCart::getIsNew, false);
        lambdaQueryWrapper.orderByDesc(StoreCart::getId);
        List<StoreCart> storeCarts = dao.selectList(lambdaQueryWrapper);
        if (CollUtil.isEmpty(storeCarts)) {
            return CommonPage.copyPageInfo(page, new ArrayList<>());
        }

        List<CartInfoResponse> response = new ArrayList<>();
        for (StoreCart storeCart : storeCarts) {
            CartInfoResponse cartInfoResponse = new CartInfoResponse();
            BeanUtils.copyProperties(storeCart, cartInfoResponse);
            // ??????????????????
            StoreProduct storeProduct = storeProductService.getCartByProId(storeCart.getProductId());
            cartInfoResponse.setImage(storeProduct.getImage());
            cartInfoResponse.setStoreName(storeProduct.getStoreName());

            if (!isValid) {// ????????????????????????
                cartInfoResponse.setAttrStatus(false);
                response.add(cartInfoResponse);
                continue ;
            }

            // ?????????????????????????????????(?????????????????????)
            List<StoreProductAttrValue> attrValueList = storeProductAttrValueService.getListByProductIdAndAttrId(storeCart.getProductId(),
                    storeCart.getProductAttrUnique(), Constants.PRODUCT_TYPE_NORMAL);
            // ????????????????????????
            if (CollUtil.isEmpty(attrValueList)) {
                cartInfoResponse.setAttrStatus(false);
                response.add(cartInfoResponse);
                continue ;
            }
            StoreProductAttrValue attrValue = attrValueList.get(0);
            // ??????????????????
            if (StrUtil.isNotBlank(attrValue.getImage())) {
                cartInfoResponse.setImage(attrValue.getImage());
            }
            cartInfoResponse.setAttrId(attrValue.getId());
            cartInfoResponse.setSuk(attrValue.getSuk());
            cartInfoResponse.setPrice(attrValue.getPrice());
            cartInfoResponse.setAttrId(attrValue.getId());
            cartInfoResponse.setAttrStatus(attrValue.getStock() > 0);
            cartInfoResponse.setStock(attrValue.getStock());
            response.add(cartInfoResponse);
        }
        return CommonPage.copyPageInfo(page, response);
    }

    /**
     * ????????????id????????????id??????
     * @param userId ??????id
     * @param cartIds ?????????id??????
     * @param isNew     ??????????????????
     * @return ???????????????
     */
    @Override
    public List<StoreCartResponse> getListByUserIdAndCartIds(Integer userId, List<String> cartIds,Boolean isNew) {
        LambdaQueryWrapper<StoreCart> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.in(StoreCart::getId,cartIds);
        lambdaQueryWrapper.eq(StoreCart::getUid, userId);
//        lambdaQueryWrapper.eq(StoreCart::getIsNew, isNew);
        lambdaQueryWrapper.orderByDesc(StoreCart::getCreateTime);
        List<StoreCart> storeCarts = dao.selectList(lambdaQueryWrapper);
        if (CollUtil.isEmpty(storeCarts)) {
            throw new CrmebException("????????????????????????");
        }

        List<StoreCartResponse> response = new ArrayList<>();

        for (StoreCart storeCart : storeCarts) {
            List<StoreProductAttrValue> productAttrValues =
                    storeProductAttrValueService.getListByProductIdAndAttrId(
                            storeCart.getProductId(),
                            storeCart.getProductAttrUnique(),
                            Constants.PRODUCT_TYPE_NORMAL);
            for (StoreProductAttrValue productAttrValue : productAttrValues) {
                StoreCartResponse storeCartResponse = new StoreCartResponse();
                BeanUtils.copyProperties(storeCart, storeCartResponse);
                StoreProductResponse product = storeProductService.getByProductId(productAttrValue.getProductId());
                StoreProductCartProductInfoResponse p = new StoreProductCartProductInfoResponse();
                BeanUtils.copyProperties(product, p);
                p.setAttrInfo(productAttrValue);
                storeCartResponse.setProductInfo(p);
                storeCartResponse.setTruePrice(productAttrValue.getPrice());
                storeCartResponse.setVipTruePrice(setVipPrice(productAttrValue.getPrice(), userId,false));
                storeCartResponse.setTrueStock(product.getStock());
                storeCartResponse.setCostPrice(product.getCost());
                response.add(storeCartResponse);
            }
        }
        return response;
    }

    /**
     * ????????????id????????????id???????????????????????????
     * @param userId ????????????id
     * @param cartIds ?????????id??????
     * @return ?????????????????????
     */
    @Override
    public List<StoreCart> getList(Integer userId, List<Integer> cartIds) {
        LambdaQueryWrapper<StoreCart> lqwStoreList = new LambdaQueryWrapper<>();
        lqwStoreList.eq(StoreCart::getUid,userId);
        lqwStoreList.eq(StoreCart::getType, "product");
        lqwStoreList.in(StoreCart::getId,cartIds);
        lqwStoreList.orderByDesc(StoreCart::getCreateTime);
        return dao.selectList(lqwStoreList);
    }

    /**
     * ???????????????
     * @param request ????????????
     * @return Map<String, Integer>
     */
    @Override
    public Map<String, Integer> getUserCount(CartNumRequest request) {
        Integer userId = userService.getUserIdException();
        Map<String, Integer> map = new HashMap<>();
        int num;
        if (request.getType().equals("total")) {
            num = getUserCountByStatus(userId, request.getNumType());
        } else {
            num = getUserSumByStatus(userId, request.getNumType());
        }
        map.put("count", num);
        return map;
    }

    /**
     * ????????????????????????
     * @param storeCart ???????????????
     * @return ????????????????????????
     */
    @Override
    public String saveCate(StoreCart storeCart) {
        // ??????????????????
        StoreProductResponse existProduct = storeProductService.getByProductId(storeCart.getProductId());
        if (ObjectUtil.isNull(existProduct) || existProduct.getIsDel()) throw new CrmebException("???????????????");
        if (!existProduct.getIsShow()) throw new CrmebException("???????????????");

        /**
         * ================================
         * ??????????????????
         * ================================
         */

        // ????????????
        if (!storeCart.getIsNew()) {
            if (ObjectUtil.isNotNull(storeCart.getSeckillId()) && storeCart.getSeckillId() > 0) {
                throw new CrmebException("?????????????????????????????????");
            }
            if (ObjectUtil.isNotNull(storeCart.getBargainId()) && storeCart.getBargainId() > 0) {
                throw new CrmebException("?????????????????????????????????");
            }
            if (ObjectUtil.isNotNull(storeCart.getCombinationId()) && storeCart.getCombinationId() > 0) {
                throw new CrmebException("?????????????????????????????????");
            }
        }
        // ????????????????????????
        if(ObjectUtil.isNotNull(storeCart.getSeckillId()) && storeCart.getSeckillId() > 0 && storeCart.getIsNew()){
            storeCart.setCartNum(1); // ????????????????????????????????????
            List<String> cacheSecKillIds = buildCartInfoForSeckill(storeCart);
            return cacheSecKillIds.get(0);
         }

        // ????????????????????????
        if (ObjectUtil.isNotNull(storeCart.getBargainId()) && storeCart.getBargainId() > 0 && storeCart.getIsNew()) {
            storeCart.setCartNum(1); // ??????????????????????????????????????????
            return buildCartInfoForBargain(storeCart);
        }

        // ????????????????????????
        if (ObjectUtil.isNotNull(storeCart.getCombinationId()) && storeCart.getCombinationId() > 0 && storeCart.getIsNew()) {
            return buildCartInfoForCombination(storeCart);
        }

        /**
         * ================================
         * ??????????????????
         * ================================
         */
        // ??????????????????????????????????????????????????????????????????????????????
        StoreCart storeCartPram = new StoreCart();
        storeCartPram.setProductAttrUnique(storeCart.getProductAttrUnique());
        storeCartPram.setUid(userService.getUserId());
        storeCartPram.setIsNew(false);
        List<StoreCart> existCarts = getByEntity(storeCartPram); // ????????????????????????????????????
        if(existCarts.size() > 0 && !storeCart.getIsNew()){ // ???????????????
            StoreCart forUpdateStoreCart = existCarts.get(0);
            forUpdateStoreCart.setCartNum(forUpdateStoreCart.getCartNum()+storeCart.getCartNum());
            boolean updateResult = updateById(forUpdateStoreCart);
            if(!updateResult) throw new CrmebException("?????????????????????");
            return forUpdateStoreCart.getId()+"";
        }else{// ????????????
            User currentUser = userService.getInfo();
            storeCart.setUid(currentUser.getUid());
            storeCart.setType("product");
            if(dao.insert(storeCart) <= 0) throw new CrmebException("?????????????????????");
            return storeCart.getId()+"";
        }
    }


    /**
     * ??????????????????
     * @param price ????????????
     * @param userId ??????id
     * @param isSingle ?????????????????????true???????????????false??????
     * @return BigDecimal
     */
    @Override
    public BigDecimal setVipPrice(BigDecimal price, Integer userId, boolean isSingle) {
        // ??????????????????????????????
        int memberFuncStatus = Integer.parseInt(systemConfigService.getValueByKey("vip_open"));
        if(memberFuncStatus <= 0){
            return price;
        }
        // ????????????
        UserLevel userLevelInfo = userLevelService.getUserLevelByUserId(userId);
        if (ObjectUtil.isNull(userLevelInfo)) return price;
        if(userLevelInfo.getDiscount().compareTo(BigDecimal.ZERO) == 0){ // ????????????????????????
            return price;
        }
        BigDecimal discount = userLevelInfo.getDiscount().divide(BigDecimal.valueOf(100));

        return isSingle ? price : discount.multiply(price).setScale(2, RoundingMode.UP);
    }

    /**
     * ?????????????????????
     * @param ids ?????????id
     * @return ??????????????????
     */
    @Override
    public boolean deleteCartByIds(List<Long> ids) {
        return dao.deleteBatchIds(ids) > 0;
    }

    /**
     * ?????????????????????
     * @param storeCart ???????????????
     * @return ?????????????????????
     */
    @Override
    public List<StoreCart> getByEntity(StoreCart storeCart) {
        LambdaQueryWrapper<StoreCart> lqw = new LambdaQueryWrapper<>();
        lqw.setEntity(storeCart);
        return dao.selectList(lqw);
    }

    /**
     * ???????????????????????? ???????????????????????????
     * @param productId ??????id
     * @return ????????????
     */
    @Override
    public Boolean productStatusNotEnable(Integer productId) {
        StoreCart storeCartPram = new StoreCart();
        storeCartPram.setProductId(productId);
        List<StoreCart> existStoreCartProducts = getByEntity(storeCartPram);
        if(null == existStoreCartProducts) return true;
        existStoreCartProducts.forEach(e-> e.setStatus(false));
        return updateBatchById(existStoreCartProducts);
    }

    /**
     * ???????????????
     * @param resetRequest ????????????
     * @return ????????????
     */
    @Override
    public boolean resetCart(CartResetRequest resetRequest) {
        LambdaQueryWrapper<StoreCart> lqw = new LambdaQueryWrapper<>();
        lqw.eq(StoreCart::getId, resetRequest.getId());
        StoreCart storeCart = dao.selectOne(lqw);
        if(null == storeCart) throw new CrmebException("??????????????????");
        if(null == resetRequest.getNum() || resetRequest.getNum() <= 0 || resetRequest.getNum() >= 999)
            throw new CrmebException("???????????????");
        storeCart.setCartNum(resetRequest.getNum());
        storeCart.setProductAttrUnique(resetRequest.getUnique()+"");
        boolean updateResult = dao.updateById(storeCart) > 0;
        if(!updateResult) throw new CrmebException("???????????????????????????");
        productStatusEnableFlag(resetRequest.getId(), true);
        return updateResult;
    }

    /**
     * ??????sku???????????????
     * @param skuIdList skuIdList
     * @return Boolean
     */
    @Override
    public Boolean productStatusNoEnable(List<Integer> skuIdList) {
        LambdaUpdateWrapper<StoreCart> lqw = new LambdaUpdateWrapper<>();
        lqw.set(StoreCart::getStatus, true);
        lqw.in(StoreCart::getProductAttrUnique, skuIdList);
        lqw.eq(StoreCart::getIsNew, false);
        return update(lqw);
    }

    /**
     * ??????????????????????????????
     * @param productId ??????id
     */
    @Override
    public Boolean productDelete(Integer productId) {
        StoreCart storeCartPram = new StoreCart();
        storeCartPram.setProductId(productId);
        List<StoreCart> existStoreCartProducts = getByEntity(storeCartPram);
        if(null == existStoreCartProducts || existStoreCartProducts.size()==0) return true;
        List<Long> cartIds = existStoreCartProducts.stream().map(StoreCart::getId).collect(Collectors.toList());
        if (CollUtil.isNotEmpty(cartIds)) {
            deleteCartByIds(cartIds);
        }
        return true;
    }

    /**
     * ??????id???uid?????????????????????
     * @param id ?????????id
     * @param uid ??????uid
     * @return StoreCart
     */
    @Override
    public StoreCart getByIdAndUid(Long id, Integer uid) {
        LambdaQueryWrapper<StoreCart> lqw = Wrappers.lambdaQuery();
        lqw.eq(StoreCart::getId, id);
        lqw.eq(StoreCart::getUid, uid);
        lqw.eq(StoreCart::getIsNew, false);
        lqw.eq(StoreCart::getStatus, true);
        return dao.selectOne(lqw);
    }

    ///////////////////////////////////////////////////////////////////???????????????
    /**
     * ?????????????????????
     * @param userId Integer ??????id
     * @param status Boolean ???????????????true-???????????????false-????????????
     * @return Integer
     */
    private Integer getUserCountByStatus(Integer userId, Boolean status) {
        //???????????????????????????
        LambdaQueryWrapper<StoreCart> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(StoreCart::getUid, userId);
        lambdaQueryWrapper.eq(StoreCart::getStatus, status);
        lambdaQueryWrapper.eq(StoreCart::getIsNew, false);
        return dao.selectCount(lambdaQueryWrapper);
    }

    /**
     * ??????????????????????????????
     * @param userId Integer ??????id
     * @param status ???????????????true-???????????????false-????????????
     * @return Integer
     */
    private Integer getUserSumByStatus(Integer userId, Boolean status) {
        QueryWrapper<StoreCart> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("ifnull(sum(cart_num), 0) as cart_num");
        queryWrapper.eq("uid", userId);
        queryWrapper.eq("is_new", false);
        queryWrapper.eq("status", status);
        StoreCart storeCart = dao.selectOne(queryWrapper);
        if (ObjectUtil.isNull(storeCart)) {
            return 0;
        }
        return storeCart.getCartNum();
    }

    /**
     * ???????????????id????????????
     * @param carId ?????????id
     * @param flag ??????????????????
     * @return ????????????
     */
    private boolean productStatusEnableFlag(Long carId,boolean flag) {
        StoreCart storeCartPram = new StoreCart();
        storeCartPram.setId(carId);
        List<StoreCart> existStoreCartProducts = getByEntity(storeCartPram);
        if(null == existStoreCartProducts) return false;
        existStoreCartProducts = existStoreCartProducts.stream().map(e->{
            e.setStatus(flag);
            return e;
        }).collect(Collectors.toList());
        return updateBatchById(existStoreCartProducts);
    }

    /**
     * ???????????????????????????
     * @param storeCartPram ????????????
     * @return  ?????????????????????????????????
     */
    private  List<String> buildCartInfoForSeckill(StoreCart storeCartPram){
        User currentUser = userService.getInfoException();
        List<String> cacheIdsResult = new ArrayList<>();
        List<StoreCartResponse> storeCartResponses = new ArrayList<>();
        StoreCartResponse storeCartResponse = new StoreCartResponse();
        StoreProductCartProductInfoResponse spcpInfo = new StoreProductCartProductInfoResponse();

        // ????????????????????????
        StoreSeckill storeSeckill = orderUtils.validSecKill(storeCartPram, currentUser);

        BeanUtils.copyProperties(storeSeckill, spcpInfo);

        // ????????????????????????????????????
        StoreProductAttrValue apAttrValuePram = new StoreProductAttrValue();
        apAttrValuePram.setProductId(storeCartPram.getSeckillId());
        apAttrValuePram.setId(Integer.valueOf(storeCartPram.getProductAttrUnique()));
        apAttrValuePram.setType(Constants.PRODUCT_TYPE_SECKILL);
        List<StoreProductAttrValue> seckillAttrValues = storeProductAttrValueService.getByEntity(apAttrValuePram);
        StoreProductAttrValue existSPAttrValue = new StoreProductAttrValue();
        if(null != seckillAttrValues && seckillAttrValues.size() > 0) existSPAttrValue = seckillAttrValues.get(0);
        if(null == existSPAttrValue) throw new CrmebException("??????????????????????????????");
        if(existSPAttrValue.getStock() <= 0) throw new CrmebException("?????????????????????");

        spcpInfo.setAttrInfo(existSPAttrValue);
        spcpInfo.setStoreInfo(storeSeckill.getInfo());
        spcpInfo.setStoreName(storeSeckill.getTitle());

        storeCartResponse.setSeckillId(storeCartPram.getSeckillId());
        storeCartResponse.setProductInfo(spcpInfo);
        storeCartResponse.setTrueStock(storeCartResponse.getProductInfo().getAttrInfo().getStock());
        storeCartResponse.setCostPrice(storeCartResponse.getProductInfo().getAttrInfo().getCost());
        storeCartResponse.setTruePrice(existSPAttrValue.getPrice());
        storeCartResponse.setVipTruePrice(BigDecimal.ZERO);

        storeCartResponse.setType(Constants.PRODUCT_TYPE_SECKILL+"");// ??????=1
        storeCartResponse.setProductId(storeCartPram.getProductId());
        storeCartResponse.setProductAttrUnique(storeCartPram.getProductAttrUnique());
        storeCartResponse.setCartNum(1);
        storeCartResponses.add(storeCartResponse);

        cacheIdsResult.add(orderUtils.setCacheOrderData(currentUser, storeCartResponses));
        return cacheIdsResult;
    }

    /**
     * ???????????????????????????
     * @param storeCartPram ????????????
     * @return  ?????????????????????????????????
     */
    private String buildCartInfoForBargain(StoreCart storeCartPram) {
        User currentUser = userService.getInfoException();
        List<StoreCartResponse> storeCartResponses = new ArrayList<>();
        StoreCartResponse storeCartResponse = new StoreCartResponse();
        StoreProductCartProductInfoResponse spcpInfo = new StoreProductCartProductInfoResponse();

        // ????????????????????????
        MyRecord record = orderUtils.validBargain(storeCartPram, currentUser);
        StoreBargain storeBargain = record.get("product");
        BeanUtils.copyProperties(storeBargain, spcpInfo);

        spcpInfo.setAttrInfo(record.get("attrInfo"));
        spcpInfo.setStoreInfo(storeBargain.getInfo());
        spcpInfo.setStoreName(storeBargain.getTitle());

        storeCartResponse.setBargainId(storeCartPram.getBargainId());
        storeCartResponse.setProductInfo(spcpInfo);
        storeCartResponse.setTrueStock(storeCartResponse.getProductInfo().getAttrInfo().getStock());
        storeCartResponse.setCostPrice(storeCartResponse.getProductInfo().getAttrInfo().getCost());
        storeCartResponse.setTruePrice(storeBargain.getMinPrice());
        storeCartResponse.setVipTruePrice(storeBargain.getMinPrice());

        storeCartResponse.setType(Constants.PRODUCT_TYPE_BARGAIN.toString());// ??????=2
        storeCartResponse.setProductId(storeCartPram.getProductId());
        storeCartResponse.setProductAttrUnique(storeCartPram.getProductAttrUnique());
        storeCartResponse.setCartNum(1);
        storeCartResponses.add(storeCartResponse);

        return orderUtils.setCacheOrderData(currentUser, storeCartResponses);
    }

    /**
     * ???????????????????????????
     * @param storeCartPram ????????????
     * @return  ?????????????????????????????????
     */
    private String buildCartInfoForCombination(StoreCart storeCartPram) {
        User currentUser = userService.getInfoException();
        List<StoreCartResponse> storeCartResponses = new ArrayList<>();
        StoreCartResponse storeCartResponse = new StoreCartResponse();
        StoreProductCartProductInfoResponse spcpInfo = new StoreProductCartProductInfoResponse();

        // ????????????????????????
        MyRecord record = orderUtils.validCombination(storeCartPram, currentUser);
        StoreCombination storeCombination = record.get("product");
        BeanUtils.copyProperties(storeCombination, spcpInfo);

        // ????????????????????????????????????
        StoreProductAttrValue apAttrValuePram = new StoreProductAttrValue();
        apAttrValuePram.setProductId(storeCartPram.getCombinationId());
        apAttrValuePram.setId(Integer.valueOf(storeCartPram.getProductAttrUnique()));
        apAttrValuePram.setType(Constants.PRODUCT_TYPE_PINGTUAN);
        List<StoreProductAttrValue> combinationAttrValues = storeProductAttrValueService.getByEntity(apAttrValuePram);
        StoreProductAttrValue existSPAttrValue = null;
        if(CollUtil.isNotEmpty(combinationAttrValues)) existSPAttrValue = combinationAttrValues.get(0);
        if(ObjectUtil.isNull(existSPAttrValue)) throw new CrmebException("??????????????????????????????");
        if(existSPAttrValue.getStock() <= 0) throw new CrmebException("?????????????????????");

        spcpInfo.setAttrInfo(record.get("attrInfo"));
        spcpInfo.setStoreInfo(storeCombination.getInfo());
        spcpInfo.setStoreName(storeCombination.getTitle());

        storeCartResponse.setCombinationId(storeCartPram.getCombinationId());
        storeCartResponse.setPinkId(Optional.ofNullable(storeCartPram.getPinkId()).orElse(0));
        storeCartResponse.setProductInfo(spcpInfo);
        storeCartResponse.setTrueStock(storeCartResponse.getProductInfo().getAttrInfo().getStock());
        storeCartResponse.setCostPrice(storeCartResponse.getProductInfo().getAttrInfo().getCost());
        storeCartResponse.setTruePrice(storeCombination.getPrice());
        storeCartResponse.setVipTruePrice(storeCombination.getPrice());

        storeCartResponse.setType(Constants.PRODUCT_TYPE_PINGTUAN.toString());// ??????=3
        storeCartResponse.setProductId(storeCartPram.getProductId());
        storeCartResponse.setProductAttrUnique(storeCartPram.getProductAttrUnique());
        storeCartResponse.setCartNum(storeCartPram.getCartNum());
        storeCartResponses.add(storeCartResponse);

        return orderUtils.setCacheOrderData(currentUser, storeCartResponses);
    }
}

