package com.bbook.controller;

import com.bbook.dto.OrderDto;
import com.bbook.dto.OrderHistDto;
import com.bbook.entity.Book;
import com.bbook.entity.Cart;
import com.bbook.entity.CartBook;
import com.bbook.entity.Order;
import com.bbook.entity.OrderBook;
import com.bbook.entity.Coupon;
import com.bbook.exception.IamportResponseException;
import com.bbook.repository.BookRepository;
import com.bbook.repository.CartBookRepository;
import com.bbook.repository.CartRepository;
import com.bbook.service.OrderService;
import com.bbook.service.SlackNotificationService;
import com.bbook.service.CartService;
import com.bbook.client.IamportClient;
import com.bbook.client.IamportResponse;
import com.bbook.dto.CancelData;
import com.bbook.dto.CartOrderDto;
import com.bbook.dto.PaymentDto;
import com.bbook.entity.Member;
import com.bbook.repository.MemberRepository;
import com.bbook.service.CouponService;
import com.bbook.config.SecurityUtil;
import com.bbook.repository.OrderRepository;
import com.bbook.repository.SubscriptionRepository;
import com.bbook.entity.Subscription;
import com.bbook.dto.OrderBookDto;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import jakarta.servlet.http.HttpSession;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.ArrayList;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.bind.annotation.ResponseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class OrderController {
	private final OrderService orderService;
	private final IamportClient iamportClient;
	private final CartService cartService;
	private final BookRepository bookRepository;
	private final MemberRepository memberRepository;
	private final CartBookRepository cartBookRepository;
	private final CouponService couponService;
	private final SecurityUtil securityUtil;
	private final CartRepository cartRepository;
	private final OrderRepository orderRepository;
	private final SlackNotificationService slackNotificationService;
	private final SubscriptionRepository subscriptionRepository;

	@GetMapping("/orders")
	public String orderHist(@RequestParam(value = "page", defaultValue = "0") int page, Principal principal,
			Model model) {
		Pageable pageable = PageRequest.of(page, 4);
		Page<OrderHistDto> ordersHistDtoList = orderService.getOrderList(principal.getName(), pageable);

		model.addAttribute("orders", ordersHistDtoList);
		model.addAttribute("page", pageable.getPageNumber());
		model.addAttribute("maxPage", 5);
		return "order/orderHist";
	}

	@GetMapping("/order/payment")
	public String orderPayment(Model model, HttpSession session, Principal principal) {
		try {
			// 사용자의 포인트 정보 조회
			Member member = memberRepository.findByEmail(principal.getName())
					.orElseThrow(() -> new EntityNotFoundException("회원 정보를 찾을 수 없습니다."));
			model.addAttribute("memberPoint", member.getPoint());

			// 사용 가능한 쿠폰 목록 조회
			List<Coupon> availableCoupons = couponService.getAvailableCoupons(member);
			model.addAttribute("availableCoupons", availableCoupons);

			// 직접 주문인 경우
			OrderDto orderDto = (OrderDto) session.getAttribute("orderDto");
			if (orderDto != null) {
				model.addAttribute("orderDto", orderDto);
				model.addAttribute("totalPrice", orderDto.getTotalPrice());
				return "order/payment";
			}

			// 장바구니 주문인 경우
			@SuppressWarnings("unchecked")
			List<CartOrderDto> cartOrderDtoList = (List<CartOrderDto>) session.getAttribute("cartOrderDtoList");
			if (cartOrderDtoList != null) {
				orderDto = cartService.createTempOrderInfo(cartOrderDtoList, principal.getName());
				model.addAttribute("orderDto", orderDto);
				model.addAttribute("totalPrice", orderDto.getTotalPrice());
				return "order/payment";
			}

			// 둘 다 없는 경우
			return "redirect:/cart";
		} catch (Exception e) {
			return "redirect:/cart";
		}
	}

	@PostMapping("/order/payment")
	@ResponseBody
	public ResponseEntity<?> orderPayment(@RequestBody Map<String, Object> payload,
			Principal principal,
			HttpSession session) {
		try {
			String email = principal.getName();

			// 세션 초기화
			session.removeAttribute("orderDto");
			session.removeAttribute("cartOrderDtoList");
			session.removeAttribute("orderEmail");

			if (payload.containsKey("bookId")) {
				// 직접 주문 처리
				OrderDto orderDto = new OrderDto();
				Long bookId = Long.parseLong(payload.get("bookId").toString());
				orderDto.setBookId(bookId);
				orderDto.setCount(Integer.parseInt(payload.get("count").toString()));
				orderDto.setTotalPrice(Long.parseLong(payload.get("totalPrice").toString()));

				// Book 정보 가져오기
				Book book = bookRepository.findById(bookId)
						.orElseThrow(() -> new EntityNotFoundException("Book not found"));

				// Member 정보 가져오기
				Member member = memberRepository.findByEmail(email)
						.orElseThrow(() -> new EntityNotFoundException("Member not found"));

				// 주문 정보 설정
				orderDto.setEmail(email);
				orderDto.setOrderName(book.getTitle());
				orderDto.setImageUrl(book.getImageUrl());
				orderDto.setTotalPrice((long) (book.getPrice() * orderDto.getCount()));
				orderDto.setMerchantUid("ORDER-" + System.currentTimeMillis());
				orderDto.setOriginalPrice(orderDto.getTotalPrice());

				// Member 정보 설정
				orderDto.setName(member.getName());
				orderDto.setPhone(member.getPhone());
				orderDto.setAddress(member.getAddress());

				// OrderBookDto 생성 및 추가
				OrderBook orderBook = OrderBook.createOrderBook(book, orderDto.getCount());
				OrderBookDto orderBookDto = new OrderBookDto(orderBook, book.getImageUrl());
				orderDto.getOrderBookDtoList().add(orderBookDto);

				// 주문 정보를 세션에 저장 (실제 주문 생성은 결제 완료 후에 수행)
				session.setAttribute("orderDto", orderDto);

			} else if (payload.containsKey("cartBooks")) {
				// 장바구니 주문 처리
				@SuppressWarnings("unchecked")
				List<Map<String, String>> cartBooks = (List<Map<String, String>>) payload.get("cartBooks");
				List<CartOrderDto> cartOrderDtoList = cartBooks.stream()
						.map(book -> {
							CartOrderDto dto = new CartOrderDto();
							dto.setCartBookId(Long.parseLong(book.get("cartBookId")));
							return dto;
						})
						.collect(Collectors.toList());

				// 장바구니 주문 정보로 OrderDto 생성
				OrderDto orderDto = cartService.createTempOrderInfo(cartOrderDtoList, email);

				// 주문 정보를 세션에 저장
				session.setAttribute("orderDto", orderDto);
				session.setAttribute("cartOrderDtoList", cartOrderDtoList);
				session.setAttribute("orderEmail", email);
			} else {
				throw new IllegalArgumentException("Invalid order request");
			}

			return ResponseEntity.ok().build();
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}

	@PostMapping("/orders/verify")
	@Transactional(isolation = Isolation.SERIALIZABLE)
	public ResponseEntity<Map<String, Object>> verifyPayment(@RequestBody PaymentDto request,
			HttpSession session) {
		Map<String, Object> response = new HashMap<>();

		try {
			// 중복 결제 검증
			synchronized (this) {
				if (orderRepository.existsByImpUid(request.getImpUid())) {
					safePaymentCancel(request.getImpUid(), "중복 결제 감지");
					throw new PaymentException("이미 처리된 결제입니다.");
				}
			}

			// 결제 정보 검증
			IamportResponse<PaymentDto> iamportResponse = iamportClient.paymentByImpUid(request.getImpUid());

			if (!isPaymentValid(iamportResponse, request)) {
				safePaymentCancel(request.getImpUid(), "결제 검증 실패");
				throw new PaymentException("결제 검증에 실패했습니다.");
			}

			try {
				// 주문 처리
				String email = securityUtil.getCurrentUsername()
						.orElseThrow(() -> new IllegalArgumentException("로그인이 필요한 서비스입니다."));
				Long orderId = processOrder(request, session, email);

				// 성공 응답
				response.put("success", true);
				response.put("orderId", orderId);

				// 세션 정리
				cleanupSession(session);

				return ResponseEntity.ok(response);

			} catch (Exception e) {
				safePaymentCancel(request.getImpUid(), "주문 처리 실패: " + e.getMessage());
				throw new PaymentException("주문 처리 중 오류가 발생했습니다: " + e.getMessage());
			}

		} catch (PaymentException e) {
			response.put("success", false);
			response.put("message", e.getMessage());
			return ResponseEntity.badRequest().body(response);
		} catch (Exception e) {
			safePaymentCancel(request.getImpUid(), "시스템 오류");
			response.put("success", false);
			response.put("message", "결제 처리 중 오류가 발생했습니다.");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	// 안전한 결제 취소 처리
	private void safePaymentCancel(String impUid, String reason) {
		try {
			CancelData cancelData = new CancelData(impUid, null, true);
			iamportClient.cancelPayment(cancelData);
		} catch (Exception e) {
			notifyAdminPaymentCancelFailure(impUid, reason, e);
		}
	}

	// 세션 데이터 정리
	private void cleanupSession(HttpSession session) {
		session.removeAttribute("orderDto");
		session.removeAttribute("cartOrderDtoList");
		session.removeAttribute("usedPoints");
		session.removeAttribute("couponDiscountAmount");
		session.removeAttribute("orderEmail");
	}

	// 결제 검증 예외 클래스
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public static class PaymentException extends RuntimeException {
		public PaymentException(String message) {
			super(message);
		}
	}

	// 관리자 알림 처리
	private void notifyAdminPaymentCancelFailure(String impUid, String reason, Exception e) {
		String message = String.format("""
				[결제 취소 실패 알림]
				결제번호: %s
				취소사유: %s
				에러메시지: %s
				""", impUid, reason, e.getMessage());

		// 슬랙 알림 전송
		try {
			slackNotificationService.sendMessage(message);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@GetMapping("/order/success/{orderId}")
	public String orderSuccess(@PathVariable Long orderId, Model model, HttpSession session) {
		try {
			Order order = orderService.findById(orderId);
			OrderDto orderDto = OrderDto.of(order);
			model.addAttribute("order", orderDto);

			// 추천 도서 데이터 추가
			List<Book> collaborativeBooks = orderService.getCollaborativeRecommendations(order.getMember().getId());
			if (collaborativeBooks.isEmpty()) {
				// 추천 도서가 없는 경우 인기 도서로 대체
				collaborativeBooks = bookRepository.findTop10ByOrderBySalesDesc();
			}
			model.addAttribute("collaborativeBooks", collaborativeBooks);

			return "order/success";
		} catch (EntityNotFoundException e) {
			return "redirect:/";
		}
	}

	@PostMapping("/order/updateQuantity")
	@ResponseBody
	public ResponseEntity<String> updateQuantity(
			@RequestBody Map<String, Object> payload,
			HttpSession session) {
		try {
			@SuppressWarnings("unchecked")
			List<CartOrderDto> cartOrderDtoList = (List<CartOrderDto>) session.getAttribute("cartOrderDtoList");

			if (cartOrderDtoList != null) {
				Long bookId = Long.parseLong(payload.get("bookId").toString());
				int newCount = Integer.parseInt(payload.get("count").toString());

				// 해당 상품의 수량 업데이트
				for (CartOrderDto dto : cartOrderDtoList) {
					if (dto.getCartBookId().equals(bookId)) {
						dto.setCount(newCount);
						break;
					}
				}

				// 업데이트된 리스트를 세션에 저장
				session.setAttribute("cartOrderDtoList", cartOrderDtoList);

				return ResponseEntity.ok("수량이 업데이트되었습니다.");
			}

			return ResponseEntity.badRequest().body("장바바구니 정보를 찾을 수 없습니다.");
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("수량 업데이트 중 오류가 발생했습니다.");
		}
	}

	@PostMapping("/order/apply-points")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> applyPoints(@RequestBody Map<String, Integer> request,
			HttpSession session, Principal principal) {
		Map<String, Object> response = new HashMap<>();

		try {
			int points = request.get("points");
			OrderDto orderDto = (OrderDto) session.getAttribute("orderDto");

			// 사용자의 보유 포인트 확인
			Member member = memberRepository.findByEmail(principal.getName())
					.orElseThrow(() -> new EntityNotFoundException("회원 정보를 찾을 수 없습니다."));

			if (points < 0) {
				response.put("success", false);
				response.put("message", "포인트는 0 이상이어야 합니다.");
				return ResponseEntity.badRequest().body(response);
			}

			if (points > member.getPoint()) {
				response.put("success", false);
				response.put("message", "보유 포인트를 초과하여 사용할 수 없습니다.");
				return ResponseEntity.badRequest().body(response);
			}

			if (points % 100 != 0) {
				response.put("success", false);
				response.put("message", "포인트는 100P 단위로 사용 가능합니다.");
				return ResponseEntity.badRequest().body(response);
			}

			// 세션에 사용할 포인트 저장
			session.setAttribute("usedPoints", points);

			// OrderDto가 있다면 포인트 적용
			if (orderDto != null) {
				// 원래 가격 보존
				Long originalPrice = orderDto.getOriginalPrice();
				// 포인트 적용된 가격 계산
				Long totalPrice = originalPrice - points;
				orderDto.setTotalPrice(totalPrice);
				session.setAttribute("orderDto", orderDto);
				response.put("updatedTotalPrice", totalPrice);
			}

			response.put("success", true);
			response.put("message", "포인트가 적용되었습니다.");
			response.put("appliedPoints", points);

			return ResponseEntity.ok(response);

		} catch (Exception e) {
			response.put("success", false);
			response.put("message", "포인트 적용 중 오류가 발생했습니다: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	// 구매 여부 검사
	@GetMapping("/orders/check/{bookId}")
	public ResponseEntity<Map<String, Boolean>> checkPurchased(
			@PathVariable Long bookId, Principal principal) {
		if (principal == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		String email = principal.getName();
		Long memberId = memberRepository.findByEmail(email).get().getId();

		boolean purchased = orderService.hasUserPurchasedBook(memberId, bookId);

		Map<String, Boolean> response = new HashMap<>();
		response.put("purchased", purchased);

		return ResponseEntity.ok(response);
	}

	@PostMapping("/order/apply-coupon")
	@ResponseBody
	public ResponseEntity<?> applyCoupon(@RequestBody Map<String, Integer> request, HttpSession session) {
		try {
			// 현재 로그인한 사용자의 이메일 가져오기
			String email = securityUtil.getCurrentUsername()
					.orElseThrow(() -> new IllegalArgumentException("로그인이 필요한 서비스입니다."));

			// 이메일로 회원 정보 조회
			Member member = memberRepository.findByEmail(email)
					.orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));

			// 주문 금액과 쿠폰 금액 검증
			Integer orderAmount = request.get("orderAmount");
			Integer couponAmount = request.get("couponAmount");

			if (orderAmount == null) {
				return ResponseEntity.badRequest().body(Map.of(
						"success", false,
						"message", "주문 금액이 필요합니다."));
			}

			// 최소 주문 금액 검증 (15,000원)
			if (orderAmount < 15000) {
				return ResponseEntity.badRequest().body(Map.of(
						"success", false,
						"message", "15,000원 이상 구매 시에만 쿠폰을 사용할 수 있습니다."));
			}

			// 쿠폰 서비스를 통해 할인 금액 검증만 수행 (쿠폰 소멸은 하지 않음)
			Integer discountAmount = couponService.validateCoupon(member, orderAmount);

			// 할인 금액이 있고, 선택한 쿠폰의 할인 금액과 일치하는 경우에만 성공 응답
			if (discountAmount > 0 && discountAmount.equals(couponAmount)) {
				// 세션에 쿠폰 할인 금액 저장
				session.setAttribute("couponDiscountAmount", discountAmount);

				return ResponseEntity.ok(Map.of(
						"success", true,
						"discountAmount", discountAmount,
						"message", "쿠폰이 적용되었습니다."));
			} else {
				// 사용 가능한 쿠폰이 없거나 할인 금액이 일치하지 않는 경우
				return ResponseEntity.badRequest().body(Map.of(
						"success", false,
						"message", "유효하지 않은 쿠폰입니다."));
			}
		} catch (Exception e) {
			// 예외 발생 시 에러 메시지 반환
			return ResponseEntity.badRequest().body(Map.of(
					"success", false,
					"message", e.getMessage()));
		}
	}

	private boolean isPaymentValid(IamportResponse<PaymentDto> iamportResponse, PaymentDto request) {
		if (iamportResponse == null || iamportResponse.getResponse() == null) {
			return false;
		}

		PaymentDto payment = iamportResponse.getResponse();
		return payment.getAmount().equals(request.getAmount()) &&
				payment.getMerchantUid().equals(request.getMerchantUid()) &&
				"paid".equals(payment.getStatus());
	}

	@PostMapping("/orders/cancel")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> cancelOrders(@RequestBody List<Long> orderIds) {
		Map<String, Object> response = new HashMap<>();
		List<CancelData> cancelDataList = new ArrayList<>();
		List<Order> orders = new ArrayList<>();

		try {
			// 1. 주문 정보 조회 및 유효성 검증
			orders = validateOrders(orderIds);
			cancelDataList = orders.stream()
					.map(order -> new CancelData(order.getImpUid(), order.getMerchantUid(), true))
					.collect(Collectors.toList());

			// 2. 아임포트 결제 취소 요청 (트랜잭션 외부 처리)
			List<IamportResponse<PaymentDto>> cancelResponses = iamportClient.cancelPayments(cancelDataList);

			// 3. 취소 성공한 주문들 처리 (트랜잭션 처리)
			return processCancelledOrders(orders, cancelResponses);

		} catch (IamportResponseException e) {
			response.put("success", false);
			response.put("message", "결제 취소 실패: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
		} catch (Exception e) {
			response.put("success", false);
			response.put("message", "주문 취소 중 오류가 발생했습니다: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	private List<Order> validateOrders(List<Long> orderIds) {
		return orderIds.stream()
				.map(orderId -> {
					Order order = orderService.findById(orderId);
					if (order.getImpUid() == null || order.getImpUid().isEmpty() ||
							order.getMerchantUid() == null || order.getMerchantUid().isEmpty()) {
						throw new IllegalStateException("주문 " + orderId + "의 결제 정보가 올바르지 않습니다.");
					}
					return order;
				})
				.collect(Collectors.toList());
	}

	@Transactional(isolation = Isolation.SERIALIZABLE)
	private ResponseEntity<Map<String, Object>> processCancelledOrders(
			List<Order> orders, List<IamportResponse<PaymentDto>> cancelResponses) {

		Map<String, Object> response = new HashMap<>();
		StringBuilder messageBuilder = new StringBuilder("주문이 성공적으로 취소되었습니다.");
		List<String> failedOrders = new ArrayList<>();

		for (int i = 0; i < orders.size(); i++) {
			Order order = orders.get(i);
			IamportResponse<PaymentDto> cancelResponse = cancelResponses.get(i);

			try {
				if (cancelResponse.getCode() == 0) {
					processSingleOrderCancellation(order, messageBuilder);
				} else {
					failedOrders.add(String.format("주문(%d): %s", order.getId(), cancelResponse.getMessage()));
				}
			} catch (Exception e) {
				failedOrders.add(String.format("주문(%d): 처리 중 오류 발생", order.getId()));
			}
		}

		if (!failedOrders.isEmpty()) {
			messageBuilder.append("\n\n취소 실패한 주문:\n").append(String.join("\n", failedOrders));
		}

		response.put("success", failedOrders.isEmpty());
		response.put("message", messageBuilder.toString());
		return ResponseEntity.ok(response);
	}

	private void processSingleOrderCancellation(Order order, StringBuilder messageBuilder) {
		// DB 주문 상태 업데이트
		orderService.cancelOrder(order.getId());

		// 포인트 정보 확인
		int usedPoints = order.getUsedPoints();
		int earnedPoints = order.getEarnedPoints();

		// 쿠폰 복원
		Member member = order.getMember();
		if (order.getIsCouponUsed()) {
			couponService.restoreCoupon(member);
		}

		// 메시지 추가
		if (usedPoints > 0 || earnedPoints > 0) {
			messageBuilder.append(String.format("\n주문(%d): 사용하신 %dP가 환불되었으며, 적립된 %dP가 차감되었습니다.",
					order.getId(), usedPoints, earnedPoints));
		}
		if (order.getIsCouponUsed()) {
			messageBuilder.append(String.format("\n주문(%d): 사용하신 쿠폰이 복원되었습니다.", order.getId()));
		}
	}

	@PostMapping("/order/cancel-points")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> cancelPoints(HttpSession session) {
		Map<String, Object> response = new HashMap<>();
		try {
			// 세션에서 사용된 포인트와 주문 정보 가져오기
			Integer usedPoints = (Integer) session.getAttribute("usedPoints");
			OrderDto orderDto = (OrderDto) session.getAttribute("orderDto");

			if (orderDto != null && usedPoints != null) {
				// 현재 금액에 사용된 포인트를 다시 더해서 원래 금액으로 복원
				Long restoredPrice = orderDto.getTotalPrice() + usedPoints;
				orderDto.setTotalPrice(restoredPrice);
				session.setAttribute("orderDto", orderDto);
				response.put("updatedTotalPrice", restoredPrice);
			}

			// 세션에서 포인트 정보 제거
			session.removeAttribute("usedPoints");

			response.put("success", true);
			response.put("message", "포인트가 취소되었습니다.");
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			response.put("success", false);
			response.put("message", "포인트 취소 중 오류가 발생했습니다: " + e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	@PostMapping("/order/cancel-coupon")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> cancelCoupon(HttpSession session, Principal principal) {
		Map<String, Object> response = new HashMap<>();
		try {
			// 세션에서 쿠폰 할인 정보 제거
			OrderDto orderDto = (OrderDto) session.getAttribute("orderDto");

			if (orderDto != null) {

				orderDto.setTotalPrice(orderDto.getOriginalPrice());

				session.setAttribute("orderDto", orderDto);

				// 업데이트된 금액 정보를 응답에 포함
				response.put("updatedTotalPrice", orderDto.getTotalPrice());
			}
			session.removeAttribute("couponDiscountAmount");

			// 회원의 쿠폰 상태 복원
			Member member = memberRepository.findByEmail(principal.getName())
					.orElseThrow(() -> new IllegalStateException("회원을 찾을 수 없습니다."));
			couponService.restoreCoupon(member);

			response.put("success", true);
			response.put("message", "쿠폰이 취소되었습니다.");
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			response.put("success", false);
			response.put("message", "쿠폰 취소 중 오류가 발생했습니다: " + e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	@PostMapping("/order/save-address")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> saveAddress(@RequestBody Map<String, String> addressData,
			Principal principal) {
		Map<String, Object> response = new HashMap<>();
		try {
			Member member = memberRepository.findByEmail(principal.getName())
					.orElseThrow(() -> new IllegalStateException("회원을 찾을 수 없습니다."));

			// 회원 정보 업데이트
			member.setName(addressData.get("receiverName"));
			member.setPhone(addressData.get("receiverPhone"));
			member.setAddress(String.format("[%s] %s %s",
					addressData.get("postcode"),
					addressData.get("address"),
					addressData.get("detailAddress")));

			memberRepository.save(member);

			response.put("success", true);
			response.put("message", "배송지가 저장되었습니다.");
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			response.put("success", false);
			response.put("message", "배송지 저장 중 오류가 발생했습니다: " + e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	private Long processOrder(PaymentDto request, HttpSession session, String email) {
		Long orderId = null;
		Integer usedPoints = (Integer) session.getAttribute("usedPoints");
		Integer couponDiscountAmount = (Integer) session.getAttribute("couponDiscountAmount");

		@SuppressWarnings("unchecked")
		List<CartOrderDto> cartOrderDtoList = (List<CartOrderDto>) session.getAttribute("cartOrderDtoList");

		if (cartOrderDtoList != null && !cartOrderDtoList.isEmpty()) {
			// 장바구니 상품 주문 처리
			orderId = cartService.orderCartBook(cartOrderDtoList, email,
					request.getImpUid(), request.getMerchantUid(),
					usedPoints != null ? usedPoints : 0,
					couponDiscountAmount != null ? couponDiscountAmount : 0);

			Member member = memberRepository.findByEmail(email)
					.orElseThrow(() -> new IllegalStateException("회원을 찾을 수 없습니다."));

			Order savedOrder = orderRepository.findById(orderId)
					.orElseThrow(() -> new IllegalStateException("주문을 찾을 수 없습니다."));

			processOrderDetails(savedOrder, member, request.getAmount());
			processCartItems(cartOrderDtoList, email);

		} else {
			// 단일 상품 주문 처리
			orderId = processSingleOrder(request, session, email);
		}

		return orderId;
	}

	private void processOrderDetails(Order order, Member member, long amount) {
		Subscription subscription = subscriptionRepository.findByMemberId(member.getId())
				.orElse(null);

		// 배송비 계산 및 설정 - 구독자는 무조건 무료배송
		long shippingFee = (subscription != null && subscription.isActive()) ? 0L
				: (order.getOriginalPrice() < 15000 ? 3000L : 0L);
		order.setShippingFee(shippingFee);
		order.setTotalPrice(amount);

		// 포인트 적립률 계산
		int earnedPoints = (subscription != null && subscription.isActive()) ? (int) (amount * 0.1) : // 구독자: 10% 적립
				(int) (amount * 0.05); // 비구독자: 5% 적립
		order.setEarnedPoints(earnedPoints);

		// 포인트 적립 처리
		member.setPoint(member.getPoint() + earnedPoints);
		memberRepository.save(member);

		orderRepository.save(order);
	}

	private void processCartItems(List<CartOrderDto> cartOrderDtoList, String email) {
		// 재고 확인 및 알림
		for (CartOrderDto cartOrderDto : cartOrderDtoList) {
			CartBook cartBook = cartBookRepository.findById(cartOrderDto.getCartBookId())
					.orElseThrow(() -> new EntityNotFoundException("장바구니 상품을 찾을 수 없습니다."));
			Book bookItem = cartBook.getBook();
			if (bookItem != null && bookItem.getStock() <= 0) {
				slackNotificationService.sendStockAlert(bookItem);
			}
		}
		// 장바구니 비우기
		cartService.deleteCartBooks(cartOrderDtoList, email);
	}

	private Long processSingleOrder(PaymentDto request, HttpSession session, String email) {
		OrderDto orderDto = (OrderDto) session.getAttribute("orderDto");
		if (orderDto == null) {
			throw new IllegalStateException("주문 정보를 찾을 수 없습니다.");
		}

		List<OrderBook> orderBookList = new ArrayList<>();
		Book book = bookRepository.findById(orderDto.getBookId())
				.orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다."));

		OrderBook orderBook = OrderBook.createOrderBook(book, orderDto.getCount());
		orderBookList.add(orderBook);

		Member member = memberRepository.findByEmail(email)
				.orElseThrow(() -> new IllegalStateException("회원을 찾을 수 없습니다."));

		Order order = Order.createOrder(member, orderBookList, request.getImpUid(),
				request.getMerchantUid());

		Integer usedPoints = (Integer) session.getAttribute("usedPoints");
		Integer couponDiscountAmount = (Integer) session.getAttribute("couponDiscountAmount");

		order.setOriginalPrice(orderDto.getOriginalPrice());
		order.setUsedPoints(usedPoints != null ? usedPoints : 0);
		order.setDiscountAmount(couponDiscountAmount != null ? couponDiscountAmount : 0);
		order.setIsCouponUsed(couponDiscountAmount != null && couponDiscountAmount > 0);

		orderRepository.save(order);
		processOrderDetails(order, member, request.getAmount());

		// 재고 확인 및 알림
		if (book.getStock() <= 0) {
			slackNotificationService.sendStockAlert(book);
		}

		// 장바구니에서 해당 상품이 있다면 삭제
		Cart cart = cartRepository.findByMemberId(member.getId());
		if (cart != null) {
			CartBook cartBook = cartBookRepository.findByCartIdAndBookId(cart.getId(), book.getId());
			if (cartBook != null) {
				cartBookRepository.delete(cartBook);
			}
		}

		return order.getId();
	}
}
