package com.example.flashsale.order;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.ArgumentMatchers.*;import static org.mockito.Mockito.*;

class OrderWorkflowTest {
 private OrderRepository repository;private ProductServiceClient products;private ReservationServiceClient reservations;private PaymentServiceClient payments;private RedisInventoryGate redisInventory;
 @BeforeEach void setup(){
  repository=mock(OrderRepository.class);products=mock(ProductServiceClient.class);reservations=mock(ReservationServiceClient.class);payments=mock(PaymentServiceClient.class);redisInventory=mock(RedisInventoryGate.class);
  when(repository.save(any())).thenAnswer(i->i.getArgument(0));
 }
 private OrderWorkflow workflow(int crash){return new OrderWorkflow(repository,products,reservations,payments,redisInventory,new SimpleMeterRegistry(),crash);}
 private OrderApi.CreateRequest request(String id){return new OrderApi.CreateRequest(id,"user-1",1L,1);}
 private void product(){when(products.get(1L)).thenReturn(new ProductServiceClient.Product(1L,"Laptop",new BigDecimal("1000"),100));}
 private ReservationServiceClient.Reservation reservation(){return new ReservationServiceClient.Reservation("reservation-1","order",1L,"user-1",1,"RESERVED");}
 @Test void successfulOrchestration(){
  product();when(reservations.create(anyString(),eq(1L),eq("user-1"),eq(1))).thenReturn(reservation());
  when(payments.process(anyString(),eq("user-1"),eq(new BigDecimal("1000")))).thenReturn(new PaymentServiceClient.Payment("payment-1","order","user-1",new BigDecimal("1000"),"SUCCESS",null));
  when(reservations.confirm("reservation-1")).thenReturn(new ReservationServiceClient.Reservation("reservation-1","order",1L,"user-1",1,"CONFIRMED"));
  OrderApi.Response response=workflow(0).create(request("req-1"));
  assertEquals(Order.Status.CONFIRMED,response.orderStatus());assertEquals("SUCCESS",response.paymentStatus());
 }
 @Test void productNotFoundMarksOrderFailed(){
  when(products.get(1L)).thenThrow(new DownstreamNotFoundException("product",new RuntimeException()));
  assertEquals("PRODUCT_NOT_FOUND",workflow(0).create(request("req-1")).failureReason());
 }
 @Test void outOfStockMarksOrderFailed(){
  product();doThrow(new DownstreamConflictException("product",new RuntimeException())).when(products).reduce(anyLong(),anyString(),anyString(),anyInt());
  assertEquals("OUT_OF_STOCK",workflow(0).create(request("req-1")).failureReason());
 }
 @Test void reservationFailureLeavesInventoryReduced(){
  product();when(reservations.create(anyString(),anyLong(),anyString(),anyInt())).thenThrow(new DownstreamUnavailableException("reservation",new RuntimeException()));
  assertThrows(DownstreamUnavailableException.class,()->workflow(0).create(request("req-1")));
  verify(products,never()).restore(anyLong(),anyString(),anyString(),anyInt());
 }
 @Test void paymentFailureCancelsAndRestoresInventory(){
  product();when(reservations.create(anyString(),anyLong(),anyString(),anyInt())).thenReturn(reservation());
  when(payments.process(anyString(),anyString(),any())).thenReturn(new PaymentServiceClient.Payment("payment-1","order","user-1",new BigDecimal("1000"),"FAILED","PAYMENT_DECLINED"));
  when(reservations.cancel("reservation-1")).thenReturn(new ReservationServiceClient.Reservation("reservation-1","order",1L,"user-1",1,"CANCELLED"));
  OrderApi.Response response=workflow(0).create(request("req-1"));assertEquals(Order.Status.FAILED,response.orderStatus());
  verify(products).restore(eq(1L),anyString(),eq("req-1"),eq(1));
 }
 @Test void paymentTimeoutLeavesPendingState(){
  product();when(reservations.create(anyString(),anyLong(),anyString(),anyInt())).thenReturn(reservation());
  when(payments.process(anyString(),anyString(),any())).thenThrow(new DownstreamTimeoutException("payment",new RuntimeException()));
  assertThrows(DownstreamTimeoutException.class,()->workflow(0).create(request("req-1")));
 }
 @Test void productTimeoutIsReported(){
  when(products.get(1L)).thenThrow(new DownstreamTimeoutException("product",new RuntimeException()));
  assertThrows(DownstreamTimeoutException.class,()->workflow(0).create(request("req-1")));
 }
 @Test void failureAfterPaymentSuccessLeavesReservationUnconfirmed(){
  product();when(reservations.create(anyString(),anyLong(),anyString(),anyInt())).thenReturn(reservation());
  when(payments.process(anyString(),anyString(),any())).thenReturn(new PaymentServiceClient.Payment("payment-1","order","user-1",BigDecimal.TEN,"SUCCESS",null));
  assertThrows(AfterPaymentSuccessException.class,()->workflow(100).create(request("req-1")));verify(reservations,never()).confirm(anyString());
 }
 @Test void duplicateRequestIdsCreateTwoOrders(){
  product();when(reservations.create(anyString(),anyLong(),anyString(),anyInt())).thenReturn(reservation());
  when(payments.process(anyString(),anyString(),any())).thenReturn(new PaymentServiceClient.Payment("payment-1","order","user-1",BigDecimal.TEN,"FAILED","DECLINED"));
  when(reservations.cancel(anyString())).thenReturn(new ReservationServiceClient.Reservation("reservation-1","order",1L,"user-1",1,"CANCELLED"));
  workflow(0).create(request("same"));workflow(0).create(request("same"));
  verify(repository,atLeast(2)).save(argThat(o->o.getRequestId().equals("same")));
 }
 @Test void redisRejectionPreventsPendingOrder(){
  doThrow(new RedisInventoryRejectedException(1L,"insufficient quantity")).when(redisInventory).reserve(1L,1);
  assertThrows(RedisInventoryRejectedException.class,()->workflow(0).create(request("req-1")));
  verify(repository,never()).save(any());
 }
}
