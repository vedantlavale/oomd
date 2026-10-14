/* VSMS client-side behaviour. Plain JavaScript, no build step.
 *  1. Mobile sidebar toggle
 *  2. Confirmation dialog for forms marked with data-confirm="..."
 *  3. Double-submit protection
 *  4. Auto-dismiss of success messages
 *  5. Dependent drop-downs (e.g. only show the vehicles of the chosen customer)
 *  6. Print buttons (data-print)
 */
(function () {
    'use strict';

    /* ---------- 1. Sidebar toggle (small screens) ---------- */
    var toggle = document.getElementById('sidebarToggle');
    var backdrop = document.getElementById('sidebarBackdrop');

    function setSidebar(open) {
        document.body.classList.toggle('sidebar-open', open);
        if (toggle) {
            toggle.setAttribute('aria-expanded', String(open));
            toggle.setAttribute('aria-label', open ? 'Close navigation menu' : 'Open navigation menu');
        }
    }
    if (toggle) {
        toggle.addEventListener('click', function () {
            setSidebar(!document.body.classList.contains('sidebar-open'));
        });
    }
    if (backdrop) {
        backdrop.addEventListener('click', function () { setSidebar(false); });
    }
    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') { setSidebar(false); }
    });

    /* ---------- 2. Confirmation dialog ---------- */
    var modalElement = document.getElementById('confirmModal');
    var pending = null;

    function openConfirm(form, submitter) {
        var title = (submitter && submitter.dataset.confirmTitle) || form.dataset.confirmTitle || 'Please confirm';
        var message = (submitter && submitter.dataset.confirm) || form.dataset.confirm;
        var label = (submitter && submitter.dataset.confirmLabel) || form.dataset.confirmLabel || 'Confirm';
        var variant = (submitter && submitter.dataset.confirmVariant) || form.dataset.confirmVariant || 'danger';

        document.getElementById('confirmModalTitle').textContent = title;
        document.getElementById('confirmModalBody').textContent = message;
        var ok = document.getElementById('confirmModalOk');
        ok.textContent = label;
        ok.className = 'btn btn-' + variant;
        pending = { form: form, submitter: submitter };
        bootstrap.Modal.getOrCreateInstance(modalElement).show();
    }

    if (modalElement) {
        document.getElementById('confirmModalOk').addEventListener('click', function () {
            if (!pending) { return; }
            var target = pending;
            pending = null;
            bootstrap.Modal.getOrCreateInstance(modalElement).hide();
            target.form.dataset.confirmed = 'true';
            if (target.form.requestSubmit) {
                target.form.requestSubmit(target.submitter || undefined);
            } else {
                target.form.submit();
            }
        });
        modalElement.addEventListener('hidden.bs.modal', function () { pending = null; });
    }

    /* ---------- 3. Double-submit protection + confirm hook ---------- */
    document.addEventListener('submit', function (event) {
        var form = event.target;
        if (!(form instanceof HTMLFormElement)) { return; }
        var submitter = event.submitter;
        var needsConfirm = (submitter && submitter.dataset.confirm) || form.dataset.confirm;

        if (needsConfirm && form.dataset.confirmed !== 'true' && modalElement) {
            event.preventDefault();
            openConfirm(form, submitter);
            return;
        }
        form.dataset.confirmed = 'false';

        // Disable the buttons just after the browser has collected the form values.
        window.setTimeout(function () {
            form.querySelectorAll('button[type="submit"], input[type="submit"]').forEach(function (button) {
                button.disabled = true;
                button.classList.add('is-submitting');
            });
        }, 0);
    });

    // Coming back with the browser's Back button must not leave buttons disabled.
    window.addEventListener('pageshow', function () {
        document.querySelectorAll('.is-submitting').forEach(function (button) {
            button.disabled = false;
            button.classList.remove('is-submitting');
        });
    });

    /* ---------- 4. Auto-dismiss success messages ---------- */
    document.querySelectorAll('.alert.auto-dismiss').forEach(function (alertElement) {
        window.setTimeout(function () {
            if (document.body.contains(alertElement)) {
                bootstrap.Alert.getOrCreateInstance(alertElement).close();
            }
        }, 7000);
    });

    /* ---------- 5. Dependent drop-downs ----------
     * <select id="vehicleId" data-depends-on="customerId"> with options carrying data-parent="<customer id>".
     * Only the options whose data-parent matches the chosen customer stay visible.
     */
    document.querySelectorAll('select[data-depends-on]').forEach(function (child) {
        var parent = document.getElementById(child.dataset.dependsOn);
        if (!parent) { return; }

        function refresh(resetSelection) {
            var chosen = parent.value;
            var stillValid = false;
            Array.prototype.forEach.call(child.options, function (option) {
                if (!option.value) { return; }
                var match = option.dataset.parent === chosen;
                option.hidden = !match;
                option.disabled = !match;
                if (match && option.selected) { stillValid = true; }
            });
            if (resetSelection && !stillValid) { child.value = ''; }
            child.disabled = !chosen;
        }
        parent.addEventListener('change', function () { refresh(true); });
        refresh(false);
    });

    /* ---------- 6. Print buttons ---------- */
    document.querySelectorAll('[data-print]').forEach(function (button) {
        button.addEventListener('click', function () { window.print(); });
    });
})();

/* ---------- 7. Booking form: grey out time slots that are already taken ----------
 * A form with data-slots-url asks the server which times are booked for the chosen service and date.
 * On the reschedule form the service is fixed (data-service-id) and the appointment's own slot stays usable.
 */
(function () {
    'use strict';
    document.querySelectorAll('form[data-slots-url]').forEach(function (form) {
        var serviceField = form.querySelector('#serviceItemId');
        var dateField = form.querySelector('#appointmentDate');
        var timeField = form.querySelector('#appointmentTime');
        if (!dateField || !timeField) { return; }

        function serviceId() { return serviceField ? serviceField.value : form.dataset.serviceId; }

        // Remember each option's own text so that marking a slot as booked is always applied to the original label.
        Array.prototype.forEach.call(timeField.options, function (option) { option.dataset.label = option.textContent; });
        var latestRequest = 0;

        function showTaken(taken, own) {
            Array.prototype.forEach.call(timeField.options, function (option) {
                var isTaken = option.value && taken.indexOf(option.value) !== -1 && option.value !== own;
                option.disabled = !!isTaken;
                option.textContent = isTaken ? option.dataset.label + ' (booked)' : option.dataset.label;
            });
        }

        function refresh() {
            var service = serviceId();
            var date = dateField.value;
            var request = ++latestRequest;
            showTaken([], null);
            if (!service || !date) { return; }
            var url = form.dataset.slotsUrl + '?serviceId=' + encodeURIComponent(service) + '&date=' + encodeURIComponent(date);
            fetch(url, { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' })
                .then(function (response) { return response.ok ? response.json() : []; })
                .then(function (taken) {
                    if (request !== latestRequest) { return; }   // a newer choice was made meanwhile
                    var own = (date === form.dataset.currentDate) ? form.dataset.currentTime : null;
                    showTaken(taken, own);
                })
                .catch(function () { /* the server still checks every booking */ });
        }

        if (serviceField) { serviceField.addEventListener('change', refresh); }
        dateField.addEventListener('change', refresh);
        refresh();
    });
})();
