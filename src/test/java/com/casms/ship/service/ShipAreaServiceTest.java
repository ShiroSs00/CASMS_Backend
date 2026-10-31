package com.casms.ship.service;
import com.casms.ship.entity.*;
import com.casms.ship.enums.*;
import com.casms.ship.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShipAreaServiceTest {
 final ShipModelRepository models=mock(ShipModelRepository.class);
 final ShipModelObjectRepository objects=mock(ShipModelObjectRepository.class);
 final ShipAreaService service=new ShipAreaService(models,objects,new ObjectMapper());
 ShipModel model(ShipModelStatus status) { var m=new ShipModel();m.setShipModelId(UUID.randomUUID());m.setStatus(status);return m; }
 @Test void absentModelIsEmptyNot422() {
  UUID ship=UUID.randomUUID();when(models.findByShipShipIdOrderByCreatedAtDesc(ship)).thenReturn(List.of());
  assertEquals(0,service.count(ship));assertTrue(service.list(ship).isEmpty());verifyNoInteractions(objects);
 }
 @Test void readyVersionWinsWithoutDoubleCountingDraft() {
  UUID ship=UUID.randomUUID();var ready=model(ShipModelStatus.READY);var draft=model(ShipModelStatus.DRAFT);
  when(models.findByShipShipIdOrderByCreatedAtDesc(ship)).thenReturn(List.of(draft,ready));
  when(objects.countByShipModelShipModelIdAndObjectTypeAndDeckIsNotNull(ready.getShipModelId(),ShipModelObjectType.FACILITY)).thenReturn(102L);
  assertEquals(102,service.count(ship));verify(objects,never()).countByShipModelShipModelIdAndObjectTypeAndDeckIsNotNull(draft.getShipModelId(),ShipModelObjectType.FACILITY);
 }
 @Test void draftMappingsWorkBeforePublicationAndExcludeOtherShips() {
  UUID shipId=UUID.randomUUID();var draft=model(ShipModelStatus.DRAFT);var archived=model(ShipModelStatus.ARCHIVED);
  when(models.findByShipShipIdOrderByCreatedAtDesc(shipId)).thenReturn(List.of(archived,draft));
  var ship=new Ship();ship.setShipId(shipId);var deck=new Deck();deck.setShip(ship);deck.setDeckId(UUID.randomUUID());deck.setDeckName("Main");deck.setDeckNumber(2);
  var area=new ShipModelObject();area.setShipModelObjectId(UUID.randomUUID());area.setDeck(deck);area.setDisplayName("Reading room");area.setMetadataJson("{\"kind\":\"library\"}");
  var other=new ShipModelObject();var otherDeck=new Deck();var otherShip=new Ship();otherShip.setShipId(UUID.randomUUID());otherDeck.setShip(otherShip);other.setDeck(otherDeck);
  when(objects.findByShipModelShipModelIdAndObjectTypeAndDeckIsNotNull(draft.getShipModelId(),ShipModelObjectType.FACILITY)).thenReturn(List.of(area,other));
  var rows=service.list(shipId);assertEquals(1,rows.size());assertEquals("library",rows.getFirst().kind());assertEquals(deck.getDeckId(),rows.getFirst().deckId());
 }
}
