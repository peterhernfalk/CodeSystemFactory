package com.example.codesys.controller;

import com.example.codesys.model.MatchedTerm;
import com.example.codesys.model.TermMatch;
import com.example.codesys.model.TermMatchResponse;
import com.example.codesys.model.TermRequest;
import com.example.codesys.model.UnmatchedTerm;
import com.example.codesys.service.SnomedService;
import com.example.codesys.service.SnomedServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TermsControllerServerChainFallbackTest {

    @Mock
    private SnomedServiceFactory serviceFactory;

    @Mock
    private SnomedService snowstormService;

    @Mock
    private SnomedService ontoserverService;

    @Mock
    private SnomedService ineraService;

    @Test
    void orderedFallbackUsesNextServerOnlyForStillUnmatchedTerms_andSetsMatchedByServer() {
        TermsController controller = new TermsController(serviceFactory);

        when(serviceFactory.getService("snowstorm")).thenReturn(snowstormService);
        when(serviceFactory.getService("ontoserver")).thenReturn(ontoserverService);
        when(serviceFactory.getService("inera")).thenReturn(ineraService);

        List<String> terms = List.of("t1", "t2", "t3");
        List<String> chain = List.of("snowstorm", "ontoserver", "inera");

        when(snowstormService.matchTerms(terms)).thenReturn(List.of(
                new TermMatch("t1", "S1", "PT1", "FSN1", 0.9, "MATCHED"),
                new TermMatch("t2", null, null, null, 0.0, "NO_MATCH"),
                new TermMatch("t3", null, null, null, 0.0, "NO_MATCH")
        ));

        when(ontoserverService.matchTerms(List.of("t2", "t3"))).thenReturn(List.of(
                new TermMatch("t2", "O2", "PT2", "FSN2", 0.8, "MATCHED"),
                new TermMatch("t3", null, null, null, 0.0, "NO_MATCH")
        ));

        when(ineraService.matchTerms(List.of("t3"))).thenReturn(List.of(
                new TermMatch("t3", "I3", "PT3", "FSN3", 0.7, "MATCHED")
        ));

        TermRequest request = new TermRequest(terms, null, chain);
        TermMatchResponse response = controller.match(request);

        // Assertions: all terms matched across the chain
        assertEquals(3, response.matched().size());
        assertEquals(0, response.unmatched().size());

        MatchedTerm m1 = response.matched().get(0);
        assertEquals("t1", m1.inputTerm());
        assertEquals("S1", m1.snomedId());
        assertEquals("snowstorm", m1.matchedByServer());

        MatchedTerm m2 = response.matched().get(1);
        assertEquals("t2", m2.inputTerm());
        assertEquals("O2", m2.snomedId());
        assertEquals("ontoserver", m2.matchedByServer());

        MatchedTerm m3 = response.matched().get(2);
        assertEquals("t3", m3.inputTerm());
        assertEquals("I3", m3.snomedId());
        assertEquals("inera", m3.matchedByServer());

        // Verify calls were only for still-unmatched terms
        verify(ontoserverService, times(1)).matchTerms(List.of("t2", "t3"));
        verify(ineraService, times(1)).matchTerms(List.of("t3"));
        verify(snowstormService, times(1)).matchTerms(terms);
    }

    @Test
    void whenAllServersFail_returnsUnmatchedReason_andDoesNotCrash() {
        TermsController controller = new TermsController(serviceFactory);

        when(serviceFactory.getService("snowstorm")).thenReturn(snowstormService);
        when(serviceFactory.getService("ontoserver")).thenReturn(ontoserverService);
        when(serviceFactory.getService("inera")).thenReturn(ineraService);

        List<String> terms = List.of("t1", "t2");
        List<String> chain = List.of("snowstorm", "ontoserver", "inera");

        when(snowstormService.matchTerms(terms)).thenReturn(List.of(
                new TermMatch("t1", null, null, null, 0.0, "NO_MATCH"),
                new TermMatch("t2", null, null, null, 0.0, "NO_MATCH")
        ));
        when(ontoserverService.matchTerms(terms)).thenReturn(List.of(
                new TermMatch("t1", null, null, null, 0.0, "NO_MATCH"),
                new TermMatch("t2", null, null, null, 0.0, "NO_MATCH")
        ));
        when(ineraService.matchTerms(terms)).thenReturn(List.of(
                new TermMatch("t1", null, null, null, 0.0, "NO_MATCH"),
                new TermMatch("t2", null, null, null, 0.0, "NO_MATCH")
        ));

        TermRequest request = new TermRequest(terms, null, chain);
        TermMatchResponse response = controller.match(request);

        assertEquals(0, response.matched().size());
        assertEquals(2, response.unmatched().size());
        for (UnmatchedTerm u : response.unmatched()) {
            assertTrue(u.reason().contains("all servers in chain failed"));
        }
    }
}

