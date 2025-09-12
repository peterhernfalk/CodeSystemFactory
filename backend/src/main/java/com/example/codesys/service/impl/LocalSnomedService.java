
package com.example.codesys.service.impl;

import com.example.codesys.model.TermMatch;
import com.example.codesys.service.SnomedService;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Primary
public class LocalSnomedService implements SnomedService {

    private static class Entry {
        String id; String svPref; String svFsn; List<String> keywords;
        Entry(String id, String svPref, String svFsn, List<String> keywords){
            this.id=id; this.svPref=svPref; this.svFsn=svFsn; this.keywords=keywords;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final JaroWinklerSimilarity jw = new JaroWinklerSimilarity();

    public LocalSnomedService() {
        try (var br = new BufferedReader(new InputStreamReader(
                new ClassPathResource("sct_demo.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String line; br.readLine();
            while((line=br.readLine())!=null){
                String[] parts = line.split(",", -1);
                if(parts.length>=4){
                    String id=parts[0].trim();
                    String svPref=parts[1].trim();
                    String svFsn=parts[2].trim();
                    List<String> keywords = Arrays.stream(parts[3].split(";"))
                            .map(String::trim).filter(s->!s.isEmpty()).toList();
                    entries.add(new Entry(id, svPref, svFsn, keywords));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load sct_demo.csv", e);
        }
    }

    @Override
    public List<TermMatch> matchTerms(List<String> terms) {
        return terms.stream().map(this::bestMatch).collect(Collectors.toList());
    }

    private TermMatch bestMatch(String input){
        String norm = input.toLowerCase(Locale.ROOT);
        double bestScore = -1; Entry best = null;
        for(Entry e: entries){
            for(String k: e.keywords){
                double s = jw.apply(norm, k.toLowerCase(Locale.ROOT));
                if(s>bestScore){ bestScore=s; best=e; }
            }
        }
        if(best==null || bestScore<0.75){
            return new TermMatch(input, null, null, null, 0.0, "NO_MATCH");
        }
        return new TermMatch(input, best.id, best.svPref, best.svFsn, bestScore, "MATCHED");
    }
}
