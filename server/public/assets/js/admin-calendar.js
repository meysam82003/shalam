/* The year starts are shared with the native admin app. Form submissions stay Gregorian. */
(function () {
  'use strict';
  const fields = document.querySelectorAll('input[type="date"],input[type="datetime-local"],input[type="time"]');
  if (!fields.length) return;
  const MS = 86400000;
  const digit = value => String(value).replace(/\d/g, d => '۰۱۲۳۴۵۶۷۸۹'[d]);
  const pad = value => String(value).padStart(2, '0');
  const months = ['فروردین','اردیبهشت','خرداد','تیر','مرداد','شهریور','مهر','آبان','آذر','دی','بهمن','اسفند'];
  const tehranNow = () => new Date(Date.now() + 12600000).toISOString().slice(0, 16);
  const dnum = iso => Date.parse(iso.slice(0, 10) + 'T00:00Z') / MS;
  const button = (label, action, style) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'btn ' + (style || 'ghost');
    btn.textContent = label;
    btn.onclick = action;
    return btn;
  };
  const select = (values, current, label, choose) => {
    const box = document.createElement('select');
    box.setAttribute('aria-label', label);
    for (const [value, text] of values) box.add(new Option(text, value, false, value === current));
    box.onchange = () => choose(Number(box.value));
    return box;
  };

  function install(cal) {
    const index = year => year - cal.first;
    const last = cal.first + cal.starts.length - 2;
    const monthOffset = month => month <= 6 ? (month - 1) * 31 : 186 + (month - 7) * 30;
    const monthDays = (year, month) => month <= 6 ? 31 : month < 12 ? 30 : cal.starts[index(year) + 1] - cal.starts[index(year)] - 336;
    const fromIso = iso => {
      const day = dnum(iso);
      if (!Number.isFinite(day) || day < cal.starts[0] || day >= cal.starts.at(-1)) return null;
      let i = cal.starts.findIndex(start => start > day) - 1;
      const offset = day - cal.starts[i];
      const month = offset < 186 ? 1 + Math.floor(offset / 31) : 7 + Math.floor((offset - 186) / 30);
      return {year:cal.first + i, month, day:1 + offset - monthOffset(month)};
    };
    const toDay = (year, month, day) => cal.starts[index(year)] + monthOffset(month) + day - 1;

    for (const input of fields) {
      if (input.disabled || input.readOnly) continue;
      const hasDate = input.type !== 'time', hasTime = input.type !== 'date';
      const describe = () => {
        if (!input.value) return hasDate && hasTime ? 'انتخاب تاریخ و ساعت' : hasDate ? 'انتخاب تاریخ' : 'انتخاب ساعت';
        const parts = hasDate && fromIso(input.value);
        const date = parts ? `${digit(parts.day)} ${months[parts.month - 1]} ${digit(parts.year)}` : input.value.slice(0,10);
        const time = digit(input.value.slice(-5));
        return hasDate ? date + (hasTime ? '، ساعت ' + time : '') : time;
      };
      const dialog = document.createElement('dialog');
      dialog.className = 'admin-calendar';
      dialog.dir = 'rtl';
      dialog.setAttribute('aria-label', hasDate ? 'تقویم شمسی' : 'انتخاب ساعت');
      if (typeof dialog.showModal !== 'function') continue;
      const trigger = button(describe(), open);
      trigger.classList.add('calendar-trigger');
      const label = input.labels?.[0]?.textContent.trim() || input.previousElementSibling?.textContent.trim();
      if (label) trigger.setAttribute('aria-label', label + '، ' + describe());
      input.before(trigger);
      input.hidden = true;
      document.body.append(dialog);
      input.addEventListener('invalid', event => { event.preventDefault(); if (!dialog.open) open(); });
      function set(value) {
        input.value = value;
        trigger.textContent = describe();
        if (label) trigger.setAttribute('aria-label', label + '، ' + describe());
        input.dispatchEvent(new Event('change', {bubbles:true}));
        dialog.close();
      }
      function open() {
        const now = tehranNow();
        const marked = fromIso(input.value) || fromIso(now);
        let {year, month, day} = marked;
        let [hour, minute] = (hasTime && input.value ? input.value.slice(-5) : now.slice(-5)).split(':').map(Number);
        function render() {
          dialog.replaceChildren();
          const title = document.createElement('h2');
          title.textContent = hasDate ? 'تقویم شمسی' : 'ساعت تهران';
          dialog.append(title);
          if (hasDate) {
            const picks = document.createElement('div');
            picks.className = 'calendar-picks';
            const years = Array.from({length:last - cal.first + 1}, (_,i) => [cal.first+i, digit(cal.first+i)]);
            function change(y,m) { year=y; month=m; day=Math.min(day,monthDays(year,month)); render(); }
            picks.append(select(years,year,'سال',y=>change(y,month)),
                         select(months.map((m,i)=>[i+1,m]),month,'ماه',m=>change(year,m)));
            dialog.append(picks);
            const days = document.createElement('div');
            days.className = 'calendar-days';
            for (const weekday of ['ش','ی','د','س','چ','پ','ج']) {
              const text = document.createElement('span'); text.textContent=weekday; days.append(text);
            }
            const start = new Date(toDay(year,month,1)*MS).getUTCDay();
            for (let i=0;i<(start+1)%7;i++) days.append(document.createElement('span'));
            for (let d=1;d<=monthDays(year,month);d++) {
              const b=button(digit(d), () => {
                day=d;
                for (const item of days.querySelectorAll('button')) {
                  const on = item === b;
                  item.classList.toggle('gold', on); item.classList.toggle('ghost', !on);
                  item.setAttribute('aria-pressed', String(on));
                }
              }, d===day?'gold':'ghost');
              b.setAttribute('aria-label', digit(d)+' '+months[month-1]);
              b.setAttribute('aria-pressed',String(d===day)); days.append(b);
            }
            dialog.append(days);
          }
          if (hasTime) {
            const time = document.createElement('div');
            time.className = 'calendar-picks';
            const numbers = count => Array.from({length:count},(_,i)=>[i,digit(pad(i))]);
            time.append(select(numbers(24),hour,'ساعت تهران',v=>hour=v), select(numbers(60),minute,'دقیقه',v=>minute=v));
            dialog.append(time);
          }
          const actions = document.createElement('div');
          actions.className = 'calendar-actions';
          actions.append(button('تأیید',() => {
            const date = new Date(toDay(year,month,day)*MS).toISOString().slice(0,10);
            const time = pad(hour)+':'+pad(minute);
            set(hasDate ? date + (hasTime ? 'T'+time : '') : time);
          },'gold'),button('انصراف',()=>dialog.close()));
          if (!input.required) actions.append(button('پاک کردن',()=>set('')));
          dialog.append(actions);
        }
        render();
        dialog.showModal();
      }
    }
  }
  fetch('../assets/calendar/iran-years.json?v=1').then(r => {
    if (!r.ok) throw new Error('calendar'); return r.json();
  }).then(install).catch(() => {});
})();
