package com.iwomi.hub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iwomi.hub.domain.Notification;
import com.iwomi.hub.repo.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Stores department notifications and pushes them live to the right people (Server-Sent Events). */
@Service
public class NotificationService {

    /** departmentIds == null means "all departments" (admins, CTO). */
    private record Subscriber(SseEmitter emitter, Set<Long> departmentIds) {}

    private final NotificationRepository repo;
    private final ObjectMapper mapper;
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    public NotificationService(NotificationRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public SseEmitter subscribe(Set<Long> departmentIds) {
        SseEmitter emitter = new SseEmitter(30L * 60 * 1000);
        Subscriber subscriber = new Subscriber(emitter, departmentIds);
        subscribers.add(subscriber);
        emitter.onCompletion(() -> subscribers.remove(subscriber));
        emitter.onTimeout(() -> subscribers.remove(subscriber));
        emitter.onError(e -> subscribers.remove(subscriber));
        try {
            emitter.send(SseEmitter.event().name("hello").data("connected"));
        } catch (IOException e) {
            subscribers.remove(subscriber);
        }
        return emitter;
    }

    public Notification notifyDepartment(Long departmentId, String title, String body, String refType, Long refId) {
        Notification n = new Notification();
        n.setDepartmentId(departmentId);
        n.setTitle(title);
        n.setBody(body);
        n.setRefType(refType);
        n.setRefId(refId);
        n = repo.save(n);
        String json;
        try {
            json = mapper.writeValueAsString(n);
        } catch (Exception e) {
            return n;
        }
        for (Subscriber s : subscribers) {
            if (s.departmentIds() != null && !s.departmentIds().contains(departmentId)) continue;
            try {
                s.emitter().send(SseEmitter.event().name("notification").data(json));
            } catch (Exception e) {
                subscribers.remove(s);
            }
        }
        return n;
    }
}
