package com.bbook.dto;

import com.bbook.constant.OrderStatus;
import com.bbook.entity.Order;
import com.bbook.entity.Book;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
public class OrderDto {
	private Long bookId;
	private int count;
	private String merchantUid;
	private String impUid;
	private String imageUrl;
	private Long orderId;

	// 결제 관련
	private String orderName; // 주문명
	private Long totalPrice; // 배송비 포함 최종 금액
	private Long originalPrice; // 배송비 제외 상품 금액
	private String email; 
	private String name;
	private String phone; 
	private String address; // 배송지
	private OrderStatus orderStatus; // 주문 상태
	private Long shippingFee;

	// 포인트와 할인 관련
	private Integer usedPoints = 0; // 사용한 포인트
	private Integer earnedPoints = 0; // 적립 예정 포인트
	private Integer discountAmount = 0; // 쿠폰 할인 금액
	private Boolean isCouponUsed = false; // 쿠폰 사용 여부

	// 주문 상품 목록 추가
	private List<OrderBookDto> orderBookDtoList = new ArrayList<>();

	// 추가된 필드
	private String detailAddress;

	private String postcode;

	@NotBlank(message = "배송 요청사항을 선택해주세요")
	private String deliveryRequest;

	private String customRequest; // 직접 입력 시 사용

	private String gatePassword; // 공동현관 비밀번호

	// Order 엔티티를 OrderDto로 변환
	public static OrderDto of(Order order) {
		OrderDto orderDto = new OrderDto();

		// Book 정보 로깅
		Book book = order.getOrderBooks().get(0).getBook();
		System.out.println("Book ID: " + book.getId());
		System.out.println("Book Title: " + book.getTitle());
		System.out.println("Book Image URL: " + book.getImageUrl());

		orderDto.setOrderName(order.getMember().getNickname() + "의 주문");
		orderDto.setBookId(book.getId());
		orderDto.setOriginalPrice(order.getOriginalPrice());
		orderDto.setTotalPrice(order.getTotalPrice());
		orderDto.setMerchantUid(order.getMerchantUid());
		orderDto.setImpUid(order.getImpUid());
		orderDto.setShippingFee(order.getShippingFee());

		// Member 정보 설정
		orderDto.setEmail(order.getMember().getEmail());
		orderDto.setName(order.getMember().getName());
		orderDto.setPhone(order.getMember().getPhone());
		orderDto.setAddress(order.getMember().getAddress());
		orderDto.setOrderStatus(order.getOrderStatus());
		orderDto.setImageUrl(book.getImageUrl());

		// 포인트와 할인 정보 설정
		orderDto.setUsedPoints(order.getUsedPoints());
		orderDto.setEarnedPoints(order.getEarnedPoints());
		orderDto.setDiscountAmount(order.getDiscountAmount());
		orderDto.setIsCouponUsed(order.getIsCouponUsed());

		// 주문 상품 목록 설정
		order.getOrderBooks().forEach(orderBook -> {
			OrderBookDto orderBookDto = new OrderBookDto(orderBook, orderBook.getBook().getImageUrl());
			orderDto.getOrderBookDtoList().add(orderBookDto);
		});

		return orderDto;
	}
}
