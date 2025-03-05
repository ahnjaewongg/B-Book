package com.bbook.entity;

import com.bbook.constant.OrderStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class Order extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Column(name = "order_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member_id")
	private Member member;

	// 주문 생성 날짜 시간
	private LocalDateTime orderDate;

	// 주문 상태(결제완료 PAID, 환불완료 CANCEL)
	@Enumerated(EnumType.STRING)
	private OrderStatus orderStatus;
	
	// 주문 상품 목록
	// mappedBy로 양방향 관계 설정, order가 주인
	// cascade로 주문상품도 함께 저장/삭제
	// orphanRemoval로 주문상품 제거시 DB에서도 삭제
	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<OrderBook> orderBooks = new ArrayList<>();

	// 주문 고유 번호
	@Column(unique = true)
	private String merchantUid;

	// 아임포트 고유 결제번호
	@Column(name = "imp_uid")
	private String impUid;

	private LocalDateTime cancelledAt;// 주문 취소된 날짜 시간
	private Long originalPrice; // 상품 원가
	private Long shippingFee; // 배송비
	private Long totalPrice; // 최종 금액 (상품 + 배송비)

	private Integer usedPoints = 0; // 사용 포인트
	private Integer earnedPoints = 0; // 적립 포인트
	private Integer discountAmount = 0; // 쿠폰 할인 금액
	private Boolean isCouponUsed = false; // 쿠폰 사용 여부

	public void addOrderBook(OrderBook orderBook) {
		orderBooks.add(orderBook);
		orderBook.setOrder(this);
	}

	public static Order createOrder(Member member, List<OrderBook> orderBookList, String impUid, String merchantUid) {
		Order order = new Order();
		order.setMember(member);
		for (OrderBook orderBook : orderBookList) {
			order.addOrderBook(orderBook);
		}
		order.setOrderStatus(OrderStatus.PAID);
		order.setOrderDate(LocalDateTime.now());
		order.setImpUid(impUid);
		order.setMerchantUid(merchantUid);
		return order;
	}

	public Long getTotalPrice() {
		if (this.totalPrice != null) {
			return this.totalPrice;
		}

		// 기존 계산 로직은 originalPrice 계산용으로 사용
		long booksTotal = orderBooks.stream()
				.mapToLong(OrderBook::getTotalPrice)
				.sum();

		this.originalPrice = booksTotal;

		// 구독자는 무조건 무료배송, 비구독자는 15,000원 미만일 때만 배송비 부과
		this.shippingFee = booksTotal < 15000 ? 3000L : 0L;

		this.totalPrice = booksTotal + this.shippingFee - (usedPoints != null ? usedPoints : 0)
				- (discountAmount != null ? discountAmount : 0);

		return this.totalPrice;
	}

	// 순수 상품 금액 조회
	public Long getOriginalPrice() {
		if (this.originalPrice == null) {
			this.originalPrice = orderBooks.stream()
					.mapToLong(OrderBook::getTotalPrice)
					.sum();
		}
		return this.originalPrice;
	}

	public void cancelOrder() {
		this.orderStatus = OrderStatus.CANCEL;
		this.cancelledAt = LocalDateTime.now();
	}

	public String getMerchantUid() {
		return merchantUid;
	}

	public void setMerchantUid(String merchantUid) {
		this.merchantUid = merchantUid;
	}

	public String getImpUid() {
		return impUid;
	}

	public void setImpUid(String impUid) {
		this.impUid = impUid;
	}

	public Integer getUsedPoints() {
		return usedPoints;
	}

	public Integer getEarnedPoints() {
		return earnedPoints;
	}

	public void setUsedPoints(Integer usedPoints) {
		this.usedPoints = usedPoints;
	}

	public void setEarnedPoints(Integer earnedPoints) {
		this.earnedPoints = earnedPoints;
	}

	public Long getShippingFee() {
		return shippingFee;
	}

	public void setShippingFee(Long shippingFee) {
		this.shippingFee = shippingFee;
	}

}
