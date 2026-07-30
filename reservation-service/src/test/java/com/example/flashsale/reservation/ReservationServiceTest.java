package com.example.flashsale.reservation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.ArgumentMatchers.any;import static org.mockito.Mockito.*;

class ReservationServiceTest {
 private ReservationRepository repository;private ReservationService service;
 @BeforeEach void setup(){repository=mock(ReservationRepository.class);when(repository.save(any())).thenAnswer(i->i.getArgument(0));service=new ReservationService(repository,new SimpleMeterRegistry(),0,0);}
 @Test void createsConfirmsAndCancelsReservations(){
  Reservation first=service.create(new ReservationApi.CreateRequest("o1",1L,"u1",1));assertEquals(Reservation.Status.RESERVED,first.getStatus());
  when(repository.findByReservationId(first.getReservationId())).thenReturn(Optional.of(first));
  assertEquals(Reservation.Status.CONFIRMED,service.confirm(first.getReservationId()).getStatus());
  Reservation second=service.create(new ReservationApi.CreateRequest("o2",1L,"u2",1));
  when(repository.findByReservationId(second.getReservationId())).thenReturn(Optional.of(second));
  assertEquals(Reservation.Status.CANCELLED,service.cancel(second.getReservationId()).getStatus());
 }
 @Test void rejectsInvalidStatusTransition(){
  Reservation r=new Reservation("r1","o1",1L,"u1",1);r.transition(Reservation.Status.CONFIRMED);
  when(repository.findByReservationId("r1")).thenReturn(Optional.of(r));
  assertThrows(InvalidReservationTransitionException.class,()->service.cancel("r1"));
 }
}
