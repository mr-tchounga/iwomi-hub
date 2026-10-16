package com.iwomi.hub.web;

import com.iwomi.hub.domain.KycDocument;
import com.iwomi.hub.repo.KycDocumentRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kyc-documents")
public class KycDocumentController extends CrudController<KycDocument, KycDocumentRepository> {
    public KycDocumentController(KycDocumentRepository repo) {
        super(repo, KycDocument::setId);
    }
}
