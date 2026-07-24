// app.js - Logic and IndexedDB management

// DOM Elements
const syncBtn = document.getElementById('syncBtn');
const syncModal = document.getElementById('syncModal');
const cancelSyncBtn = document.getElementById('cancelSyncBtn');
const startSyncBtn = document.getElementById('startSyncBtn');
const pinInput = document.getElementById('pinInput');
const loader = document.getElementById('loader');
const loaderText = document.getElementById('loaderText');

const navItems = document.querySelectorAll('.nav-item');
const tabContents = document.querySelectorAll('.tab-content');

// IndexedDB Setup
const DB_NAME = 'SFW_MobileDB';
const DB_VERSION = 1;
let db;

function initDB() {
    return new Promise((resolve, reject) => {
        const request = indexedDB.open(DB_NAME, DB_VERSION);
        
        request.onupgradeneeded = (event) => {
            const database = event.target.result;
            if (!database.objectStoreNames.contains('stock')) {
                database.createObjectStore('stock', { keyPath: 'id', autoIncrement: true });
            }
            if (!database.objectStoreNames.contains('lrs')) {
                database.createObjectStore('lrs', { keyPath: 'id', autoIncrement: true });
            }
            if (!database.objectStoreNames.contains('creditors')) {
                database.createObjectStore('creditors', { keyPath: 'id', autoIncrement: true });
            }
        };
        
        request.onsuccess = (event) => {
            db = event.target.result;
            resolve();
        };
        
        request.onerror = (event) => reject(event.target.error);
    });
}

// Navigation Logic
navItems.forEach(item => {
    item.addEventListener('click', () => {
        navItems.forEach(nav => nav.classList.remove('active'));
        tabContents.forEach(tab => tab.classList.add('hidden'));
        tabContents.forEach(tab => tab.classList.remove('active'));
        
        item.classList.add('active');
        const targetId = item.getAttribute('data-target');
        const targetTab = document.getElementById(targetId);
        targetTab.classList.remove('hidden');
        targetTab.classList.add('active');
    });
});

// Sync UI Logic
syncBtn.addEventListener('click', () => {
    syncModal.classList.remove('hidden');
    pinInput.value = '';
    pinInput.focus();
});

cancelSyncBtn.addEventListener('click', () => {
    syncModal.classList.add('hidden');
});

startSyncBtn.addEventListener('click', async () => {
    const pin = pinInput.value.trim();
    if (!pin) {
        alert("Please enter the PIN");
        return;
    }
    
    startSyncBtn.disabled = true;
    syncModal.classList.add('hidden');
    loader.classList.remove('hidden');
    
    try {
        // Fetch data from desktop server (same origin when served from desktop)
        // If testing on a different device, we assume the PWA was served from the desktop IP
        const response = await fetch(`/api/sync?pin=${pin}`);
        
        if (!response.ok) {
            if (response.status === 401) throw new Error("Invalid PIN");
            throw new Error(`Server error: ${response.status}`);
        }
        
        const data = await response.json();
        
        // Save to IndexedDB
        await clearAndSaveData('stock', data.stock || []);
        await clearAndSaveData('lrs', data.lrs || []);
        await clearAndSaveData('creditors', data.creditors || []);
        
        loaderText.textContent = "Success!";
        setTimeout(() => {
            loader.classList.add('hidden');
            loaderText.textContent = "Syncing data...";
            startSyncBtn.disabled = false;
            loadAllDataToUI(); // Refresh UI
        }, 1000);
        
    } catch (error) {
        loader.classList.add('hidden');
        startSyncBtn.disabled = false;
        alert("Sync Failed: " + error.message);
    }
});

function clearAndSaveData(storeName, dataArray) {
    return new Promise((resolve, reject) => {
        const transaction = db.transaction(storeName, 'readwrite');
        const store = transaction.objectStore(storeName);
        
        // Clear existing
        store.clear();
        
        // Add new
        dataArray.forEach(item => store.add(item));
        
        transaction.oncomplete = () => resolve();
        transaction.onerror = (e) => reject(e.target.error);
    });
}

function getAllData(storeName) {
    return new Promise((resolve, reject) => {
        const transaction = db.transaction(storeName, 'readonly');
        const store = transaction.objectStore(storeName);
        const request = store.getAll();
        
        request.onsuccess = () => resolve(request.result);
        request.onerror = (e) => reject(e.target.error);
    });
}

// UI Rendering Logic
async function loadAllDataToUI() {
    const stock = await getAllData('stock');
    const lrs = await getAllData('lrs');
    const creditors = await getAllData('creditors');
    
    renderStock(stock);
    renderLRs(lrs);
    renderCreditors(creditors);
    
    setupSearch('stockSearch', stock, renderStock, (item, term) => 
        (item.product || '').toLowerCase().includes(term) || 
        (item.location || '').toLowerCase().includes(term)
    );
    
    setupSearch('lrSearch', lrs, renderLRs, (item, term) => 
        (item.lr_number || '').toLowerCase().includes(term) || 
        (item.transport || '').toLowerCase().includes(term)
    );
    
    setupSearch('creditorSearch', creditors, renderCreditors, (item, term) => 
        (item.supplier || '').toLowerCase().includes(term)
    );
}

function renderStock(data) {
    const container = document.getElementById('stockList');
    container.innerHTML = '';
    
    if (data.length === 0) {
        container.innerHTML = '<div class="card"><p style="text-align:center; color:var(--text-muted)">No stock data.</p></div>';
        return;
    }
    
    data.forEach(item => {
        const div = document.createElement('div');
        div.className = 'card';
        div.innerHTML = `
            <div class="card-header">
                <span class="card-title">${item.product || 'Unknown'}</span>
                <span class="card-badge">${item.location || '-'}</span>
            </div>
            <div class="card-row">
                <span>Cartons</span>
                <span>${item.cartons || 0}</span>
            </div>
            <div class="card-row">
                <span>Pairs / Carton</span>
                <span>${item.ppc || 0}</span>
            </div>
            <div class="card-row">
                <span>Total Pairs</span>
                <span>${item.total_pairs || 0}</span>
            </div>
        `;
        container.appendChild(div);
    });
}

function renderLRs(data) {
    const container = document.getElementById('lrList');
    container.innerHTML = '';
    
    if (data.length === 0) {
        container.innerHTML = '<div class="card"><p style="text-align:center; color:var(--text-muted)">No LR data.</p></div>';
        return;
    }
    
    // Sort by date descending
    const sorted = [...data].sort((a,b) => new Date(b.lr_date || 0) - new Date(a.lr_date || 0));
    
    sorted.forEach(item => {
        const div = document.createElement('div');
        div.className = 'card';
        div.innerHTML = `
            <div class="card-header">
                <span class="card-title">${item.lr_number || '-'}</span>
                <span class="card-badge" style="background:#f1f5f9; color:#475569">${item.lr_date || '-'}</span>
            </div>
            <div class="card-row">
                <span>Transport</span>
                <span>${item.transport || '-'}</span>
            </div>
            <div class="card-row">
                <span>Cartons</span>
                <span>${item.total_cartons || 0}</span>
            </div>
        `;
        container.appendChild(div);
    });
}

function renderCreditors(data) {
    const container = document.getElementById('creditorList');
    container.innerHTML = '';
    
    if (data.length === 0) {
        container.innerHTML = '<div class="card"><p style="text-align:center; color:var(--text-muted)">No Creditor data.</p></div>';
        return;
    }
    
    data.forEach(item => {
        const balance = item.amount || 0;
        const balClass = balance > 0 ? 'text-danger' : '';
        
        const div = document.createElement('div');
        div.className = 'card';
        div.innerHTML = `
            <div class="card-header">
                <span class="card-title">${item.supplier || 'Unknown'}</span>
            </div>
            <div class="card-row">
                <span>Current Balance</span>
                <span class="${balClass}">₹${balance.toLocaleString()}</span>
            </div>
        `;
        container.appendChild(div);
    });
}

function setupSearch(inputId, data, renderFn, filterFn) {
    const input = document.getElementById(inputId);
    input.addEventListener('input', (e) => {
        const term = e.target.value.toLowerCase();
        if (!term) {
            renderFn(data);
            return;
        }
        const filtered = data.filter(item => filterFn(item, term));
        renderFn(filtered);
    });
}

// Boot
initDB().then(() => {
    loadAllDataToUI();
}).catch(err => {
    console.error("Failed to initialize DB:", err);
});
