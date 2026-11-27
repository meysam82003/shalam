const fragment = window.location.hash;
const open = document.getElementById('open');
if (/^#[a-f0-9]{32}\.[A-Za-z0-9_-]{43}$/.test(fragment)) {
    open.href = 'divan://transfer/' + fragment;
    document.getElementById('open-row').hidden = false;
}
