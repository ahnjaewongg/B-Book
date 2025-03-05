package com.bbook.service;

import com.bbook.entity.Coupon;
import com.bbook.entity.Member;
import com.bbook.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 읽기 전용 트랜잭션 설정 (성능 최적화)
public class CouponService {

    private final CouponRepository couponRepository;

    // 신규 회원에게 기본 쿠폰을 발급
    // 최대 10개까지 미사용 쿠폰을 보유할 수 있으며, 부족한 만큼 추가 발급
    @Transactional
    public void createBasicCoupons(Member member) {
        // 신규 회원에게 기본 쿠폰 10개 발급
        for (int i = 0; i < 10; i++) {
            Coupon coupon = Coupon.createBasicCoupon(member);
            couponRepository.save(coupon);
        }
    }

    // 회원이 사용 가능한 쿠폰 목록 조회
    public List<Coupon> getAvailableCoupons(Member member) {
        return couponRepository.findByMemberAndIsUsedFalse(member);
    }

    // 주문에 쿠폰을 적용하고 할인 금액을 반환
    // 쿠폰 적용 조건:
    // 1. 주문 금액이 15,000원 이상
    // 2. 사용 가능한 쿠폰이 있어야 함
    // 3. 주문 금액이 쿠폰의 최소 주문 금액 이상
    @Transactional
    public Integer applyCoupon(Member member, Integer orderAmount) {
        // 최소 주문 금액 체크
        if (orderAmount < 15000) {
            return 0;
        }

        // 사용 가능한 쿠폰 조회
        Optional<Coupon> unusedCoupon = couponRepository.findFirstByMemberAndIsUsedFalse(member);
        if (unusedCoupon.isEmpty()) {
            return 0;
        }

        Coupon coupon = unusedCoupon.get();
        // 쿠폰별 최소 주문 금액 체크
        if (orderAmount < coupon.getMinimumOrderAmount()) {
            return 0;
        }

        // 쿠폰 사용 처리
        coupon.setIsUsed(true);
        return coupon.getDiscountAmount();
    }

    // 주문 취소 시 사용된 쿠폰 복원
    @Transactional
    public void restoreCoupon(Member member) {
        // 회원의 사용된 쿠폰 중 가장 최근 쿠폰을 찾아서 상태를 복원
        Optional<Coupon> coupon = couponRepository.findFirstByMemberAndIsUsedTrue(member);
        if (coupon.isPresent()) {
            coupon.get().setIsUsed(false);
            couponRepository.save(coupon.get());
        }
    }

    // 쿠폰 다운로드
    // 동일한 템플릿의 쿠폰은 중복 발급이 불가능
    @Transactional
    public void downloadCoupon(Member member) {
        // 이미 쿠폰존에서 쿠폰을 발급받았는지 확인
        if (couponRepository.existsByMemberAndCouponType(member, Coupon.CouponType.COUPON_ZONE)) {
            throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
        }

        Coupon coupon = Coupon.createCouponZoneCoupon(member);
        couponRepository.save(coupon);
    }

    // 쿠폰존에서 발급받은 쿠폰이 있는지 확인
    public boolean hasDownloadedCoupon(Member member) {
        return couponRepository.existsByMemberAndCouponType(member, Coupon.CouponType.COUPON_ZONE);
    }

    // 쿠폰 유효성 검증만 수행하는 메서드
    public Integer validateCoupon(Member member, Integer orderAmount) {
        // 최소 주문 금액 체크
        if (orderAmount < 15000) {
            return 0;
        }

        List<Coupon> coupons = couponRepository.findByMemberAndIsUsedFalse(member);
        if (!coupons.isEmpty()) {
            return 1000; // 쿠폰 할인 금액
        }
        return 0;
    }
    
    // 쿠폰 소멸
    @Transactional
    public void consumeCoupon(Member member) {
        Optional<Coupon> unusedCoupon = couponRepository.findFirstByMemberAndIsUsedFalse(member);
        if (unusedCoupon.isPresent()) {
            Coupon coupon = unusedCoupon.get();
            coupon.setIsUsed(true);
            couponRepository.save(coupon);
        } else {
            throw new IllegalStateException("사용 가능한 쿠폰이 없습니다.");
        }
    }
}