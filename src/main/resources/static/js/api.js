/*
 * Клиент API FindSport для форм веб-интерфейса.
 * Сессия — cookie JSESSIONID; CSRF-токен берется из cookie XSRF-TOKEN и передается в X-XSRF-TOKEN (ТЗ п. 4.13.5).
 * Ошибки — RFC 7807; ошибки полей (422) показываются у соответствующих полей формы.
 */
const FindSport = (() => {
    function csrfToken() {
        const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
        return match ? decodeURIComponent(match[1]) : null;
    }

    async function ensureCsrf() {
        if (!csrfToken()) {
            await fetch('/api/v1/regions', {credentials: 'same-origin'});
        }
    }

    async function request(method, url, body) {
        const headers = {'Accept': 'application/json'};
        if (method !== 'GET') {
            await ensureCsrf();
            headers['X-XSRF-TOKEN'] = csrfToken();
        }
        const multipart = body instanceof FormData;
        if (body !== undefined && !multipart) {
            headers['Content-Type'] = 'application/json';
        }
        const response = await fetch(url, {
            method, headers, credentials: 'same-origin',
            body: body === undefined || multipart ? body : JSON.stringify(body)
        });
        const text = await response.text();
        const data = text ? JSON.parse(text) : null;
        if (!response.ok) {
            const error = new Error((data && data.detail) || 'Не удалось выполнить запрос');
            error.status = response.status;
            error.problem = data || {};
            throw error;
        }
        return data;
    }

    function param(name) {
        return new URLSearchParams(window.location.search).get(name);
    }

    function show(element, text, kind) {
        element.textContent = text;
        element.className = 'alert alert-' + kind;
    }

    function hide(element) {
        element.className = 'alert hidden';
        element.textContent = '';
    }

    function clearFieldErrors(form) {
        form.querySelectorAll('.field-error').forEach(node => node.remove());
        form.querySelectorAll('[aria-invalid]').forEach(node => node.removeAttribute('aria-invalid'));
    }

    /** Показывает ошибку: у полей (422) или общим сообщением. */
    function showError(form, alertBox, error) {
        clearFieldErrors(form);
        const fieldErrors = (error.problem && error.problem.errors) || [];
        let shownAtFields = false;
        fieldErrors.forEach(({field, message}) => {
            const input = form.querySelector(`[name="${field}"]`)
                || form.querySelector(`[data-error-for="${field}"]`);
            if (!input) {
                return;
            }
            shownAtFields = true;
            input.setAttribute('aria-invalid', 'true');
            const note = document.createElement('div');
            note.className = 'field-error';
            note.textContent = message;
            (input.closest('.field, .check') || input).appendChild(note);
        });
        if (!shownAtFields || fieldErrors.length === 0) {
            show(alertBox, error.message, 'error');
        } else {
            hide(alertBox);
        }
    }

    /** Связывает форму с обработчиком: блокировка кнопки на время запроса, вывод ошибок. */
    function bindForm(form, alertBox, handler) {
        form.addEventListener('submit', async event => {
            event.preventDefault();
            const button = form.querySelector('[type=submit]');
            button.disabled = true;
            hide(alertBox);
            clearFieldErrors(form);
            try {
                await handler(new FormData(form));
            } catch (error) {
                showError(form, alertBox, error);
            } finally {
                button.disabled = false;
            }
        });
    }

    /** Элемент DOM без innerHTML: пользовательские данные выводятся только как текст (ТЗ п. 4.13.5). */
    function el(tag, attrs, ...children) {
        const node = document.createElement(tag);
        Object.entries(attrs || {}).forEach(([key, value]) => {
            if (value === null || value === undefined || value === false) {
                return;
            }
            if (key.startsWith('on')) {
                node.addEventListener(key.slice(2), value);
            } else if (key === 'className' || key === 'textContent') {
                node[key] = value;
            } else {
                node.setAttribute(key, value === true ? '' : value);
            }
        });
        children.flat().forEach(child => {
            if (child !== null && child !== undefined && child !== false) {
                node.append(child instanceof Node ? child : String(child));
            }
        });
        return node;
    }

    const ZONE = 'Europe/Minsk';

    /** Дата и время в часовом поясе Беларуси (ТЗ п. 4.2.17). */
    function dateTime(iso) {
        return iso ? new Date(iso).toLocaleString('ru-RU', {timeZone: ZONE, day: '2-digit', month: '2-digit',
            year: 'numeric', hour: '2-digit', minute: '2-digit'}) : '—';
    }

    /** ISO-момент → значение поля datetime-local по минскому времени. */
    function toLocalInput(iso) {
        return iso ? new Date(iso).toLocaleString('sv-SE', {timeZone: ZONE}).replace(' ', 'T').slice(0, 16) : '';
    }

    /** Значение datetime-local → ISO со смещением Минска (UTC+3, без перехода на летнее время). */
    function fromLocalInput(value) {
        return value ? value + ':00+03:00' : null;
    }

    /** Подписи статусов карточки; REJECTED показывается автору как «Требует доработки» (решение PM). */
    const STATUS_BADGES = {DRAFT: '', ON_MODERATION: 'badge-amber', PUBLISHED: 'badge-green', REJECTED: 'badge-red',
        ARCHIVED: ''};

    function statusBadge(status, label) {
        return el('span', {className: 'badge ' + (STATUS_BADGES[status] || '')}, label || status);
    }

    return {
        get: url => request('GET', url),
        post: (url, body) => request('POST', url, body),
        put: (url, body) => request('PUT', url, body),
        del: url => request('DELETE', url),
        upload: (url, formData) => request('POST', url, formData),
        param, show, hide, bindForm, showError, el, dateTime, toLocalInput, fromLocalInput, statusBadge
    };
})();
