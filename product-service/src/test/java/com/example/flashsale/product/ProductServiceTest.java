package com.example.flashsale.product;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductServiceTest {
 private ProductRepository repository; private ProductService service;
 @BeforeEach void setUp(){
  repository=mock(ProductRepository.class);
  when(repository.save(any(Product.class))).thenAnswer(i->i.getArgument(0));
  service=new ProductService(repository,new SimpleMeterRegistry(),0,0);
 }
 @Test void createsAndRetrievesProduct(){
  Product created=service.create(new ProductDtos.CreateProductRequest("Laptop",new BigDecimal("1000"),100));
  assertEquals("Laptop",created.getName());
  when(repository.findById(1L)).thenReturn(Optional.of(created));
  assertSame(created,service.get(1));
 }
 @Test void reducesAndRestoresInventory(){
  Product p=new Product("Laptop",new BigDecimal("1000"),10);when(repository.findById(1L)).thenReturn(Optional.of(p));
  assertEquals(8,service.reduce(1,2).getAvailableQuantity());
  assertEquals(10,service.restore(1,2).getAvailableQuantity());
 }
 @Test void rejectsInsufficientInventory(){
  when(repository.findById(1L)).thenReturn(Optional.of(new Product("Laptop",BigDecimal.ONE,1)));
  assertThrows(OutOfStockException.class,()->service.reduce(1,2));
 }
 @Test void concurrentLearningTestShowsTwoCallersCanBothSucceed() throws Exception {
  AtomicInteger reads=new AtomicInteger();
  when(repository.findById(1L)).thenAnswer(i->{reads.incrementAndGet();return Optional.of(new Product("Laptop",BigDecimal.ONE,1));});
  ExecutorService pool=Executors.newFixedThreadPool(2);
  try{
   Future<Product> first=pool.submit(()->service.reduce(1,1));
   Future<Product> second=pool.submit(()->service.reduce(1,1));
   assertEquals(0,first.get().getAvailableQuantity());
   assertEquals(0,second.get().getAvailableQuantity());
   assertEquals(2,reads.get(),"Both callers observed the same one available item");
  }finally{pool.shutdownNow();}
 }
}
