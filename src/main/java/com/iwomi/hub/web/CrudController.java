package com.iwomi.hub.web;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.BiConsumer;

/** Minimal CRUD for entities that have a Long id. */
public abstract class CrudController<T, R extends JpaRepository<T, Long>> {

    protected final R repo;
    private final BiConsumer<T, Long> idSetter;

    protected CrudController(R repo, BiConsumer<T, Long> idSetter) {
        this.repo = repo;
        this.idSetter = idSetter;
    }

    @GetMapping
    public List<T> list() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public T get(@PathVariable Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public T create(@RequestBody T body) {
        idSetter.accept(body, null);
        return repo.save(body);
    }

    @PutMapping("/{id}")
    public T update(@PathVariable Long id, @RequestBody T body) {
        if (!repo.existsById(id)) throw new NotFoundException("Not found: " + id);
        idSetter.accept(body, id);
        return repo.save(body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!repo.existsById(id)) throw new NotFoundException("Not found: " + id);
        repo.deleteById(id);
    }
}
