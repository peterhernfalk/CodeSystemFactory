
package com.example.codesys.service;

import com.example.codesys.model.TermMatch;
import java.util.List;

public interface SnomedService {
    List<TermMatch> matchTerms(List<String> terms);
}
