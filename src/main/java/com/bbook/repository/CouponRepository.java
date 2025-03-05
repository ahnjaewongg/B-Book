package com.bbook.repository;

import com.bbook.entity.Coupon;
import com.bbook.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
    List<Coupon> findByMemberAndIsUsedFalse(Member member);
    Optional<Coupon> findFirstByMemberAndIsUsedFalse(Member member);
    Optional<Coupon> findFirstByMemberAndIsUsedTrue(Member member);
    boolean existsByMemberAndCouponType(Member member, Coupon.CouponType couponType);
}