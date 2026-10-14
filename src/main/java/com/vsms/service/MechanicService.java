package com.vsms.service;

import com.vsms.dto.MechanicForm;
import com.vsms.entity.Mechanic;
import com.vsms.entity.ServiceJob;
import com.vsms.enums.JobStatus;
import com.vsms.exception.BusinessRuleException;
import com.vsms.exception.ResourceNotFoundException;
import com.vsms.repository.MechanicRepository;
import com.vsms.repository.ServiceJobRepository;
import com.vsms.util.Paging;
import com.vsms.util.Texts;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MechanicService {

    private final MechanicRepository mechanics;
    private final ServiceJobRepository jobs;

    public MechanicService(MechanicRepository mechanics, ServiceJobRepository jobs) {
        this.mechanics = mechanics;
        this.jobs = jobs;
    }

    /** A mechanic with their recent work (used by the detail page). */
    public record MechanicDetails(Mechanic mechanic, List<ServiceJob> recentJobs, long openJobCount) {
    }

    @Transactional(readOnly = true)
    public Page<Mechanic> search(String query, Boolean active, int page) {
        return mechanics.search(Texts.like(query), active, Paging.of(page, Sort.by("name", "id")));
    }

    @Transactional(readOnly = true)
    public List<Mechanic> findActive() {
        return mechanics.findByActiveTrueOrderByName();
    }

    @Transactional(readOnly = true)
    public List<Mechanic> findAll() {
        return mechanics.findAllByOrderByName();
    }

    @Transactional(readOnly = true)
    public Mechanic get(Long id) {
        return mechanics.findById(id).orElseThrow(() -> new ResourceNotFoundException("Mechanic", id));
    }

    @Transactional(readOnly = true)
    public MechanicDetails details(Long id) {
        Mechanic mechanic = get(id);
        return new MechanicDetails(mechanic, jobs.findTop20ByMechanicIdOrderByIdDesc(id),
                jobs.countByMechanicIdAndStatusIn(id, JobStatus.MECHANIC_OPEN));
    }

    public Mechanic create(MechanicForm form) {
        Mechanic mechanic = new Mechanic();
        apply(mechanic, form);
        return mechanics.save(mechanic);
    }

    public Mechanic update(Long id, MechanicForm form) {
        Mechanic mechanic = get(id);
        apply(mechanic, form);
        return mechanic;
    }

    /** A mechanic who still has open jobs must be replaced on those jobs first. */
    public Mechanic deactivate(Long id) {
        Mechanic mechanic = get(id);
        long open = jobs.countByMechanicIdAndStatusIn(id, JobStatus.MECHANIC_OPEN);
        if (open > 0) {
            throw new BusinessRuleException(("%s still has %d open job(s). Assign those jobs to another mechanic "
                    + "before deactivating.").formatted(mechanic.getName(), open));
        }
        mechanic.setActive(false);
        return mechanic;
    }

    public Mechanic activate(Long id) {
        Mechanic mechanic = get(id);
        mechanic.setActive(true);
        return mechanic;
    }

    private void apply(Mechanic mechanic, MechanicForm form) {
        mechanic.setName(Texts.clean(form.getName()));
        mechanic.setSpecialization(Texts.clean(form.getSpecialization()));
        mechanic.setPhone(Texts.clean(form.getPhone()));
    }
}
