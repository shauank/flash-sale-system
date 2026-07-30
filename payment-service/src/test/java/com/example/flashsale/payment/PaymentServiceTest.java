package com.example.flashsale.payment;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.ArgumentMatchers.any;import static org.mockito.Mockito.*;

class PaymentServiceTest {
 private PaymentRepository repository(){
  PaymentRepository r=mock(PaymentRepository.class);
  when(r.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));when(r.save(any())).thenAnswer(i->i.getArgument(0));return r;
 }
 @Test void succeedsAndPersists(){
  PaymentRepository r=repository();Payment p=new PaymentService(r,new SimpleMeterRegistry(),0,0).process(new PaymentApi.Request("o1","u1",BigDecimal.TEN));
  assertEquals(Payment.Status.SUCCESS,p.getStatus());verify(r).saveAndFlush(any());verify(r).save(any());
 }
 @Test void failsWhenConfigured(){
  Payment p=new PaymentService(repository(),new SimpleMeterRegistry(),0,100).process(new PaymentApi.Request("o1","u1",BigDecimal.TEN));
  assertEquals(Payment.Status.FAILED,p.getStatus());assertEquals("PAYMENT_DECLINED",p.getFailureReason());
 }
 @Test void appliesConfiguredBlockingDelay(){
  long start=System.nanoTime();new PaymentService(repository(),new SimpleMeterRegistry(),40,0).process(new PaymentApi.Request("o1","u1",BigDecimal.TEN));
  assertTrue((System.nanoTime()-start)/1_000_000>=35);
 }
}
