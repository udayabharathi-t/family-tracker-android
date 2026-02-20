const CLIENT_ID = 'YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com';
const SPREADSHEET_ID = 'YOUR_SPREADSHEET_ID_FROM_URL';
const SCOPES = 'https://www.googleapis.com/auth/spreadsheets.readonly';

let tokenClient;
let gapiInited = false;
let gisInited = false;
let map;

function initMap() {
    map = new google.maps.Map(document.getElementById("map"), {
        zoom: 3,
        center: { lat: 0, lng: 0 },
    });
}

function gisLoaded() {
    tokenClient = google.accounts.oauth2.initTokenClient({
        client_id: CLIENT_ID,
        scope: SCOPES,
        callback: (resp) => { handleAuthResponse(resp); },
    });
    gisInited = true;
}

function handleAuthClick() {
    tokenClient.requestAccessToken({ prompt: 'consent' });
}

async function handleAuthResponse(response) {
    if (response.error !== undefined) throw (response);
    document.getElementById('auth_button').innerText = 'Refresh Token';
    await listSheets();
}

async function listSheets() {
    const url = `https://sheets.googleapis.com/v4/spreadsheets/${SPREADSHEET_ID}?fields=sheets.properties`;
    const response = await fetch(url, {
        headers: { 'Authorization': `Bearer ${gapi.client.getToken().access_token}` }
    });
    // Note: In a pure fetch flow, use the token from handleAuthResponse
    // Filtering logic:
    const data = await response.json();
    const selector = document.getElementById('sheet_selector');
    selector.innerHTML = '<option value="">Select an Email</option>';

    data.sheets.forEach(sheet => {
        const name = sheet.properties.title;
        if (name !== "Sheet1") {
            let opt = document.createElement('option');
            opt.value = name;
            opt.innerHTML = name;
            selector.appendChild(opt);
        }
    });
    selector.style.display = 'block';
}

async function updateMapForUser() {
    const sheetName = document.getElementById('sheet_selector').value;
    if (!sheetName) return;

    const range = `${sheetName}!A:D`;
    const url = `https://sheets.googleapis.com/v4/spreadsheets/${SPREADSHEET_ID}/values/${range}`;

    // Fetch all rows for that sheet
    const response = await fetch(url, {
        headers: { 'Authorization': `Bearer ${accessToken}` } // stored from auth callback
    });
    const data = await response.json();

    if (data.values && data.values.length > 0) {
        const lastRow = data.values[data.values.length - 1];
        const lat = parseFloat(lastRow[1]);
        const lng = parseFloat(lastRow[2]);
        const battery = lastRow[3];

        const pos = { lat, lng };
        map.setCenter(pos);
        map.setZoom(15);
        new google.maps.Marker({
            position: pos,
            map: map,
            title: `Battery: ${battery}%`
        });
    }
}