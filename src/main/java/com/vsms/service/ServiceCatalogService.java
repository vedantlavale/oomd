package com.vsms.service;

import com.vsms.dto.CatalogItemForm;
import com.vsms.entity.ServiceCatalogItem;
import com.vsms.exception.BusinessRuleException;
import com.vsms.exception.ResourceNotFoundException;
import com.vsms.repository.AppointmentRepository;
import com.vsms.repository.ServiceCatalogItemRepository;
import com.vsms.util.MoneyUtil;
import com.vsms.util.Paging;
import com.vsms.util.Texts;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ServiceCatalogService {

    private final ServiceCatalogItemRepository items;
    private final AppointmentRepository appointments;

    public ServiceCatalogService(ServiceCatalogItemRepository items, AppointmentRepository appointments) {
        this.items = items;
        this.appointments = appointments;
    }

    @Transactional(readOnly = true)
    public Page<ServiceCatalogItem> search(String query, Boolean active, int page) {
        return items.search(Texts.like(query), active, Paging.of(page, Sort.by("name")));
    }

    @Transactional(readOnly = true)
    public List<ServiceCatalogItem> findActive() {
        return items.findByActiveTrueOrderByName();
    }

    @Transactional(readOnly = true)
    public List<ServiceCatalogItem> findAll() {
        return items.findAllByOrderByName();
    }

    @Transactional(readOnly = true)
    public ServiceCatalogItem get(Long id) {
        return items.findById(id).orElseThrow(() -> new ResourceNotFoundException("Service", id));
    }

    @Transactional(readOnly = true)
    public boolean isUsed(Long id) {
        return appointments.countByServiceItemId(id) > 0;
    }

    public ServiceCatalogItem create(CatalogItemForm form) {
        ServiceCatalogItem item = new ServiceCatalogItem();
        apply(item, form, false);
        return saveChecked(item);
    }

    public ServiceCatalogItem update(Long id, CatalogItemForm form) {
        ServiceCatalogItem item = get(id);
        apply(item, form, true);
        return saveChecked(item);
    }

    public ServiceCatalogItem setActive(Long id, boolean active) {
        ServiceCatalogItem item = get(id);
        item.setActive(active);
        return item;
    }

    /** A service that was ever booked is kept (deactivate it instead). */
    public void delete(Long id) {
        ServiceCatalogItem item = get(id);
        if (isUsed(id)) {
            throw new BusinessRuleException("\"" + item.getName() + "\" has been booked before and cannot be deleted. "
                    + "Mark it inactive instead.");
        }
        items.delete(item);
    }

    private void apply(ServiceCatalogItem item, CatalogItemForm form, boolean existing) {
        String name = Texts.clean(form.getName());
        Optional<ServiceCatalogItem> duplicate = items.findByNameIgnoreCase(name);
        if (duplicate.isPresent() && (!existing || !duplicate.get().getId().equals(item.getId()))) {
            throw new BusinessRuleException("name", "A service called \"" + name + "\" already exists.");
        }
        item.setName(name);
        item.setDescription(Texts.clean(form.getDescription()));
        item.setStandardPrice(MoneyUtil.money(form.getStandardPrice()));
        item.setActive(form.isActive());
    }

    private ServiceCatalogItem saveChecked(ServiceCatalogItem item) {
        try {
            return items.saveAndFlush(item);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("name", "A service called \"" + item.getName() + "\" already exists.");
        }
    }
}
