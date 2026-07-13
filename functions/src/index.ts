import {setGlobalOptions} from "firebase-functions/v2";
import {onValueCreated, onValueWritten} from "firebase-functions/v2/database";
import {onCall, HttpsError} from "firebase-functions/v2/https";
import {onSchedule} from "firebase-functions/v2/scheduler";
import {defineSecret} from "firebase-functions/params";
import * as admin from "firebase-admin";
import Razorpay from "razorpay";
import * as crypto from "crypto";
// @ts-ignore
import PaytmChecksum from "paytmchecksum";
import { calculateOrderTotal } from "./pricing";

admin.initializeApp()

const RAZORPAY_KEY_ID = defineSecret("RAZORPAY_KEY_ID");
const RAZORPAY_KEY_SECRET = defineSecret("RAZORPAY_KEY_SECRET");
const ADMIN_CODE = defineSecret("ADMIN_CODE");

setGlobalOptions({
    maxInstances: 10,
    secrets: [RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET, ADMIN_CODE]
});

interface PaymentProvider {
    createOrder(amount: number, orderId: string, canteenId: string, credentials: any): Promise<any>;
    verifySignature(paymentData: any, credentials: any): Promise<boolean>;
    initiateRefund(paymentId: string, amount: number, orderId: string, credentials: any): Promise<string>;
}

class RazorpayAdapter implements PaymentProvider {
    async createOrder(amount: number, orderId: string, canteenId: string, credentials: any) {
        const instance = new Razorpay({
            key_id: credentials.keyId,
            key_secret: credentials.keySecret,
        });
        const order = await instance.orders.create({
            amount: amount,
            currency: "INR",
            receipt: `receipt_${orderId}`,
        });
        return {
            orderId: order.id,
            gatewayData: {
                keyId: credentials.keyId
            }
        };
    }

    async verifySignature(paymentData: any, credentials: any) {
        const { razorpayOrderId, razorpayPaymentId, razorpaySignature } = paymentData;
        const hmac = crypto.createHmac("sha256", credentials.keySecret);
        hmac.update(razorpayOrderId + "|" + razorpayPaymentId);
        const generatedSignature = hmac.digest("hex");
        return generatedSignature === razorpaySignature;
    }

    async initiateRefund(paymentId: string, amount: number, orderId: string, credentials: any) {
        const instance = new Razorpay({
            key_id: credentials.keyId,
            key_secret: credentials.keySecret,
        });
        const refund = await instance.payments.refund(paymentId, {
            amount: amount,
            notes: { orderId: orderId }
        });
        return refund.id;
    }
}

class PaytmAdapter implements PaymentProvider {
    async createOrder(amount: number, orderId: string, canteenId: string, credentials: any) {
        const paytmParams: any = {};
        paytmParams.body = {
            "requestType": "Payment",
            "mid": credentials.mid,
            "websiteName": "DEFAULT",
            "orderId": orderId,
            "callbackUrl": `https://securegw.paytm.in/theia/paytmCallback?ORDER_ID=${orderId}`,
            "txnAmount": {
                "value": (amount / 100).toFixed(2),
                "currency": "INR",
            },
            "userInfo": {
                "custId": canteenId,
            },
        };

        const checksum = await PaytmChecksum.generateSignature(JSON.stringify(paytmParams.body), credentials.merchantKey);
        paytmParams.head = {
            "signature": checksum
        };

        const response = await fetch(`https://securegw.paytm.in/theia/api/v1/initiateTransaction?mid=${credentials.mid}&orderId=${orderId}`, {
            method: 'POST',
            body: JSON.stringify(paytmParams),
            headers: { 'Content-Type': 'application/json' }
        });

        const result = await response.json();
        if (result.body.resultInfo.resultStatus !== 'S') {
            throw new Error(result.body.resultInfo.resultMsg);
        }

        return {
            orderId: orderId,
            gatewayData: {
                mid: credentials.mid,
                txnToken: result.body.txnToken,
                callbackUrl: paytmParams.body.callbackUrl
            }
        };
    }

    async verifySignature(paymentData: any, credentials: any) {
        const paytmParams: any = {};
        paytmParams.body = {
            "mid": credentials.mid,
            "orderId": paymentData.orderId,
        };

        const checksum = await PaytmChecksum.generateSignature(JSON.stringify(paytmParams.body), credentials.merchantKey);
        paytmParams.head = {
            "signature": checksum
        };

        const response = await fetch(`https://securegw.paytm.in/merchant-status/api/v1/getTranscationStatus`, {
            method: 'POST',
            body: JSON.stringify(paytmParams),
            headers: { 'Content-Type': 'application/json' }
        });

        const result = await response.json();
        return result.body.resultInfo.resultStatus === 'TXN_SUCCESS';
    }

    async initiateRefund(paymentId: string, amount: number, orderId: string, credentials: any) {
        const paytmParams: any = {};
        paytmParams.body = {
            "mid": credentials.mid,
            "txnId": paymentId,
            "orderId": orderId,
            "refundId": `refund_${orderId}_${Date.now()}`,
            "refundAmount": (amount / 100).toFixed(2),
        };

        const checksum = await PaytmChecksum.generateSignature(JSON.stringify(paytmParams.body), credentials.merchantKey);
        paytmParams.head = {
            "signature": checksum
        };

        const response = await fetch(`https://securegw.paytm.in/refund/api/v1/async/refund`, {
            method: 'POST',
            body: JSON.stringify(paytmParams),
            headers: { 'Content-Type': 'application/json' }
        });

        const result = await response.json();
        if (result.body.resultInfo.resultStatus !== 'S' && result.body.resultInfo.resultStatus !== 'PENDING') {
            throw new Error(result.body.resultInfo.resultMsg);
        }
        return paytmParams.body.refundId;
    }
}

class MockAdapter implements PaymentProvider {
    async createOrder(amount: number, orderId: string, canteenId: string, credentials: any) {
        return {
            orderId: orderId,
            gatewayData: {
                message: "Development Mode: Mock Transaction"
            }
        };
    }

    async verifySignature(paymentData: any, credentials: any) {
        return true;
    }

    async initiateRefund(paymentId: string, amount: number, orderId: string, credentials: any) {
        return `mock_refund_${Date.now()}`;
    }
}

class PaymentFactory {
    static getProvider(provider: string): PaymentProvider {
        switch (provider.toUpperCase()) {
            case "RAZORPAY": return new RazorpayAdapter();
            case "PAYTM": return new PaytmAdapter();
            case "MOCK": return new MockAdapter();
            default: throw new Error(`Unsupported provider: ${provider}`);
        }
    }
}

async function getCanteenCredentials(canteenId: string, provider: string) {
    if (provider.toUpperCase() === "MOCK") return {};

    const canteenSnapshot = await admin.database().ref(`canteens/${canteenId}`).once("value");
    const canteen = canteenSnapshot.val();
    if (!canteen) throw new Error("Canteen not found");

    const secretSnapshot = await admin.firestore().collection("payment_secrets").doc(canteenId).get();
    const secrets = secretSnapshot.data();
    if (!secrets) throw new Error("Canteen payment secrets not configured");

    if (provider.toUpperCase() === "RAZORPAY") {
        return {
            keyId: canteen.providerAccountId,
            keySecret: secrets.razorpayKeySecret
        };
    } else if (provider.toUpperCase() === "PAYTM") {
        return {
            mid: canteen.providerAccountId,
            merchantKey: secrets.paytmMerchantKey
        };
    }
    throw new Error("Invalid provider for credentials");
}

function isCanteenAvailable(canteen: any): boolean {
    const mode = canteen.availabilityMode || "AUTO";
    if (mode === "FORCE_OPEN") return true;
    if (mode === "FORCE_CLOSED") return false;

    // AUTO logic
    if (canteen.open24Hours) return true;
    if (!canteen.openingTime || !canteen.closingTime) return false;

    const now = new Date();
    // IST offset is UTC+5:30.
    // This is a simplified check, for production consider a proper timezone library.
    const istTime = new Date(now.getTime() + (330 * 60 * 1000));
    const hours = istTime.getUTCHours();
    const minutes = istTime.getUTCMinutes();
    const currentTimeString = `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}`;

    const opening = canteen.openingTime;
    const closing = canteen.closingTime;

    if (opening < closing) {
        return currentTimeString >= opening && currentTimeString < closing;
    } else {
        return currentTimeString >= opening || currentTimeString < closing;
    }
}

async function requireCanteenOwner(uid: string, canteenId: string) {
    const ownerSnapshot = await admin.database().ref(`canteen_owners/${uid}`).once("value");
    if (ownerSnapshot.val() !== canteenId) {
        throw new HttpsError("permission-denied", "You are not authorized for this canteen");
    }
}

async function getVendorCredential(canteenId: string) {
    const credentialSnapshot = await admin.database().ref(`vendor_credentials/${canteenId}/vendorCode`).once("value");
    if (credentialSnapshot.exists()) return credentialSnapshot.val();

    // Transitional fallback only. The migration removes this legacy field before
    // production rules stop exposing the old canteen document shape.
    return (await admin.database().ref(`canteens/${canteenId}/vendorCode`).once("value")).val();
}

export const vendorLogin = onCall(async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "User must be authenticated contextually");

    const { canteenId, vendorCode } = request.data;
    if (!canteenId || !vendorCode) {
        throw new HttpsError("invalid-argument", "Missing required parameters");
    }

    const canteenSnapshot = await admin.database().ref(`canteens/${canteenId}`).once("value");
    const canteen = canteenSnapshot.val();
    if (!canteen) throw new HttpsError("not-found", "Canteen not found");

    // Brute-force protection
    const securityRef = admin.database().ref(`vendor_credentials/${canteenId}/security`);
    const securitySnapshot = await securityRef.once("value");
    const security = securitySnapshot.val() || {};

    if (security.failedAttempts >= 5 && Date.now() - security.lastAttempt < 5 * 60 * 1000) {
        throw new HttpsError("resource-exhausted", "Too many failed attempts. Please try again in 5 minutes.");
    }

    if (await getVendorCredential(canteenId) !== vendorCode) {
        await securityRef.update({
            failedAttempts: (security.failedAttempts || 0) + 1,
            lastAttempt: Date.now()
        });
        throw new HttpsError("unauthenticated", "Invalid vendor credentials");
    }

    // Success: clear security attempts AND set mapping
    await securityRef.remove();
    await admin.database().ref(`canteen_owners/${request.auth.uid}`).set(canteenId);

    return {
        success: true,
        canteenName: canteen.name
    };
});

export const adminLogin = onCall(async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "User must be authenticated contextually");

    const { adminCode } = request.data;
    if (!adminCode) {
        throw new HttpsError("invalid-argument", "Missing required parameters");
    }

    const correctCode = ADMIN_CODE.value();

    if (adminCode !== correctCode) {
        throw new HttpsError("unauthenticated", "Invalid admin code");
    }

    await admin.database().ref(`admin_users/${request.auth.uid}`).set(true);

    return { success: true };
});

export const initiatePayment = onCall(async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "User must be logged in");

    const { cartItems, canteenId, orderId } = request.data;
    if (!cartItems || !Array.isArray(cartItems) || cartItems.length === 0) {
        throw new HttpsError("invalid-argument", "Cart items are required");
    }

    const canteenSnapshot = await admin.database().ref(`canteens/${canteenId}`).once("value");
    const canteen = canteenSnapshot.val();
    if (!canteen) throw new HttpsError("not-found", "Canteen not found");

    let requestedOrder: any = null;
    if (orderId) {
        const orderSnapshot = await admin.database().ref(`orders/${orderId}`).once("value");
        requestedOrder = orderSnapshot.val();
        if (!requestedOrder || requestedOrder.userId !== request.auth.uid || requestedOrder.canteenId !== canteenId) {
            throw new HttpsError("permission-denied", "Order does not belong to the current user");
        }
        if (requestedOrder.status !== "AWAITING_PAYMENT") {
            throw new HttpsError("failed-precondition", "Order is not awaiting payment");
        }
    }

    // Validate canteen availability before initiating the payment flow
    if (!isCanteenAvailable(canteen)) {
        throw new HttpsError("failed-precondition", "Ordering is currently unavailable as the canteen is closed.");
    }

    let normalizedCartItems = cartItems;
    if (cartItems.some((item: any) => !item || typeof item.menuItemId !== "string" || item.menuItemId.trim().length === 0)) {
        if (!orderId || typeof orderId !== "string") {
            throw new HttpsError("failed-precondition", "Order items are missing menu identifiers");
        }

        const menuSnapshot = await admin.database().ref(`menus/${canteenId}`).once("value");
        const menu = menuSnapshot.val() || {};
        normalizedCartItems = (requestedOrder.items || []).map((item: any) => {
            if (item.menuItemId || item.id) {
                return { menuItemId: item.menuItemId || item.id, quantity: item.quantity };
            }
            const matches = Object.entries(menu)
                .filter(([, menuItem]: [string, any]) => menuItem?.name === item.name)
                .map(([menuItemId]) => menuItemId);
            if (matches.length !== 1) {
                throw new HttpsError("failed-precondition", `Cannot safely identify legacy item ${item.name || ""}`);
            }
            return { menuItemId: matches[0], quantity: item.quantity };
        });
    }

    let pricing;
    try {
        pricing = await calculateOrderTotal(canteenId, normalizedCartItems, requestedOrder?.orderType || "DINE_IN");
    } catch (error: any) {
        throw new HttpsError("invalid-argument", error.message);
    }

    // Repair legacy orders once their items have been resolved against the
    // canonical menu and recalculate legacy packaging totals. New orders
    // already persist this shape.
    if (orderId) {
        await admin.database().ref(`orders/${orderId}`).update({
            items: pricing.validatedItems,
            itemsTotal: pricing.itemsTotal,
            packagingFee: pricing.packagingFee,
            totalAmount: pricing.totalAmount
        });
    }

    try {
        const amount = pricing.totalAmount * 100; // Convert to paise
        const provider = canteen.paymentProvider || "PAYTM";
        const credentials = await getCanteenCredentials(canteenId, provider);
        const gatewayOrderId = `ORDER_${Date.now()}_${request.auth.uid.substring(0, 5)}`;
        const paymentProvider = PaymentFactory.getProvider(provider);
        const paymentData = await paymentProvider.createOrder(amount, gatewayOrderId, canteenId, credentials);
        return {
            ...paymentData,
            amount,
            currency: "INR",
            provider
        };
    } catch (error: any) {
        console.error("Error initiating payment:", error);
        throw new HttpsError("failed-precondition", error.message || "Payment provider configuration is incomplete");
    }
});

export const placeOrder = onCall(async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "User must be logged in");

    const { orderDetails } = request.data;
    const canteenId = orderDetails.canteenId;
    const uid = request.auth.uid;

    const canteenSnapshot = await admin.database().ref(`canteens/${canteenId}`).once("value");
    const canteen = canteenSnapshot.val();
    if (!canteen) throw new HttpsError("not-found", "Canteen not found");

    if (!isCanteenAvailable(canteen)) {
        throw new HttpsError("failed-precondition", "Ordering is currently unavailable as the canteen is closed.");
    }

    // Server-side price calculation and validation
    let pricing;
    try {
        pricing = await calculateOrderTotal(canteenId, orderDetails.items, orderDetails.orderType);
    } catch (error: any) {
        throw new HttpsError("invalid-argument", error.message);
    }

    const orderId = admin.database().ref("orders").push().key;
    if (!orderId) throw new HttpsError("internal", "Failed to generate order ID");

    // Generate unique pickup identifiers
    const pickupToken = crypto.randomBytes(16).toString("hex");
    let pickupCode = "";
    let isUnique = false;
    let attempts = 0;

    while (!isUnique && attempts < 5) {
        pickupCode = Math.floor(100000 + Math.random() * 900000).toString();
        const existingOrder = await admin.database().ref("orders")
            .orderByChild("canteenId_status_pickupCode")
            .equalTo(`${canteenId}_READY_${pickupCode}`)
            .once("value");

        if (!existingOrder.exists()) {
            isUnique = true;
        }
        attempts++;
    }

    const orderData = {
        ...orderDetails,
        orderId,
        userId: uid,
        totalAmount: pricing.totalAmount,
        packagingFee: pricing.packagingFee,
        itemsTotal: pricing.itemsTotal,
        items: pricing.validatedItems,
        status: "PLACED",
        paymentStatus: "PENDING",
        timestamp: Date.now(),
        statusTimestamps: { PLACED: Date.now() },
        pickupToken,
        pickupCode,
        canteenId_status_pickupCode: `${canteenId}_PLACED_${pickupCode}`
    };

    try {
        await admin.database().ref(`orders/${orderId}`).set(orderData);
        await admin.database().ref(`carts/${uid}/${canteenId}`).remove();
        return { success: true, orderId };
    } catch (error: any) {
        console.error("Error placing order:", error);
        throw new HttpsError("internal", "Failed to place order");
    }
});

export const verifyPayment = onCall(async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "User must be logged in");

    const { orderId, paymentData, resultData } = request.data;
    const orderSnapshot = await admin.database().ref(`orders/${orderId}`).once("value");
    const order = orderSnapshot.val();

    if (!order) throw new HttpsError("not-found", "Order not found");
    if (order.userId !== request.auth.uid) throw new HttpsError("permission-denied", "Unauthorized access to order");
    if (order.status !== "AWAITING_PAYMENT") throw new HttpsError("failed-precondition", "Order is not in awaiting payment status");
    if (Date.now() > order.paymentDueAt) throw new HttpsError("failed-precondition", "Payment window has expired");

    const canteenSnapshot = await admin.database().ref(`canteens/${order.canteenId}`).once("value");
    const canteen = canteenSnapshot.val();
    const provider = order.paymentProvider || canteen.paymentProvider || "PAYTM";
    const credentials = await getCanteenCredentials(order.canteenId, provider);
    const paymentProvider = PaymentFactory.getProvider(provider);

    const isValid = await paymentProvider.verifySignature({ ...paymentData, ...resultData }, credentials);
    if (!isValid) throw new HttpsError("invalid-argument", "Invalid payment signature");

    // Idempotency: Ensure this payment hasn't been processed already
    if (resultData.paymentId) {
        const existingPayment = await admin.database().ref("orders")
            .orderByChild("paymentId")
            .equalTo(resultData.paymentId)
            .once("value");
        if (existingPayment.exists()) {
            return { success: true, message: "Payment already verified" };
        }
    }

    const now = Date.now();
    const updates = {
        status: "PREPARING",
        paymentStatus: "CAPTURED",
        paymentId: resultData.paymentId,
        paymentData: paymentData,
        paymentCompletedAt: now,
        "statusTimestamps/PREPARING": now
    };

    try {
        await admin.database().ref(`orders/${orderId}`).update(updates);

        // Notify Vendor
        const tokensSnapshot = await admin.database().ref(`vendor_credentials/${order.canteenId}/fcmTokens`).once("value");
        const tokenEntries = Object.entries(tokensSnapshot.val() ?? {})
            .filter((entry): entry is [string, string] => typeof entry[1] === "string" && entry[1].length > 0);

        const title = "Payment Received";
        const body = `Payment received for ${order.studentName}'s order. You can now start preparing.`;

        for (const [tokenKey, token] of tokenEntries) {
            try {
                await admin.messaging().send({
                    token,
                    data: { title, body, type: "PAYMENT_RECEIVED", orderId },
                    android: { priority: "high" },
                });
            } catch (error: any) {
                if (error.code === "messaging/registration-token-not-registered") {
                    await admin.database().ref(`vendor_credentials/${order.canteenId}/fcmTokens/${tokenKey}`).remove();
                }
            }
        }

        return { success: true };
    } catch (error: any) {
        console.error("Error verifying payment:", error);
        throw new HttpsError("internal", "Failed to verify payment");
    }
});

export const verifyAndPlaceOrder = onCall(async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "User must be logged in");

    const { paymentData, resultData, orderDetails } = request.data;
    const canteenId = orderDetails.canteenId;

    const canteenSnapshot = await admin.database().ref(`canteens/${canteenId}`).once("value");
    const canteen = canteenSnapshot.val();
    if (!canteen) throw new HttpsError("not-found", "Canteen not found");

    // Re-validate availability just before final placement
    if (!isCanteenAvailable(canteen)) {
        throw new HttpsError("failed-precondition", "Ordering is currently unavailable as the canteen has just closed.");
    }

    const provider = canteen.paymentProvider || "PAYTM";
    const credentials = await getCanteenCredentials(canteenId, provider);
    const paymentProvider = PaymentFactory.getProvider(provider);

    const isValid = await paymentProvider.verifySignature({ ...paymentData, ...resultData }, credentials);
    if (!isValid) throw new HttpsError("invalid-argument", "Invalid payment signature");

    // Idempotency: Ensure this payment hasn't been processed already
    if (resultData.paymentId) {
        const existingPayment = await admin.database().ref("orders")
            .orderByChild("paymentId")
            .equalTo(resultData.paymentId)
            .once("value");
        if (existingPayment.exists()) {
            return { success: true, message: "Payment already verified" };
        }
    }

    // Server-side price calculation and validation
    let pricing;
    try {
        pricing = await calculateOrderTotal(canteenId, orderDetails.items, orderDetails.orderType);
    } catch (error: any) {
        throw new HttpsError("invalid-argument", error.message);
    }

    const uid = request.auth.uid;
    const orderId = admin.database().ref("orders").push().key;
    if (!orderId) throw new HttpsError("internal", "Failed to generate order ID");

    // Generate unique pickup identifiers
    const pickupToken = crypto.randomBytes(16).toString("hex");
    let pickupCode = "";
    let isUnique = false;
    let attempts = 0;

    while (!isUnique && attempts < 5) {
        pickupCode = Math.floor(100000 + Math.random() * 900000).toString();
        const existingOrder = await admin.database().ref("orders")
            .orderByChild("canteenId_status_pickupCode")
            .equalTo(`${canteenId}_READY_${pickupCode}`)
            .once("value");

        if (!existingOrder.exists()) {
            isUnique = true;
        }
        attempts++;
    }

    const orderData = {
        ...orderDetails,
        orderId,
        userId: uid,
        totalAmount: pricing.totalAmount,
        packagingFee: pricing.packagingFee,
        itemsTotal: pricing.itemsTotal,
        items: pricing.validatedItems,
        status: "PLACED",
        paymentStatus: "CAPTURED",
        paymentProvider: provider,
        paymentId: resultData.paymentId,
        paymentData: paymentData,
        timestamp: Date.now(),
        statusTimestamps: { PLACED: Date.now() },
        pickupToken,
        pickupCode,
        canteenId_status_pickupCode: `${canteenId}_PLACED_${pickupCode}` // For uniqueness check
    };

    try {
        await admin.database().ref(`orders/${orderId}`).set(orderData);
        await admin.database().ref(`carts/${uid}/${canteenId}`).remove();
        return { success: true, orderId };
    } catch (error: any) {
        console.error("Error placing order:", error);
        throw new HttpsError("internal", "Failed to place order");
    }
});

export const updateCanteenPaymentSettings = onCall(async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "User must be logged in");

    const { canteenId, provider, accountId, secret } = request.data;

    const canteenSnapshot = await admin.database().ref(`canteens/${canteenId}`).once("value");
    const canteen = canteenSnapshot.val();
    if (!canteen) throw new HttpsError("not-found", "Canteen not found");
    await requireCanteenOwner(request.auth.uid, canteenId);

    await admin.database().ref(`canteens/${canteenId}`).update({
        paymentProvider: provider,
        providerAccountId: accountId,
        paymentStatus: "ACTIVE"
    });

    const secretData: any = {};
    if (provider.toUpperCase() === "RAZORPAY") secretData.razorpayKeySecret = secret;
    else if (provider.toUpperCase() === "PAYTM") secretData.paytmMerchantKey = secret;

    await admin.firestore().collection("payment_secrets").doc(canteenId).set(secretData, { merge: true });

    return { success: true };
});

export const newOrderPlaced = onValueCreated(
    "/orders/{orderId}",

    async (event) => {
        const order = event.data?.val();
        const orderId = event.params.orderId;

        if (!order || order.status !== "PLACED" || !order.canteenId) {
            return;
        }

        const tokensSnapshot = await admin.database()
            .ref(`vendor_credentials/${order.canteenId}/fcmTokens`)
            .once("value");
        const tokenEntries = Object.entries(tokensSnapshot.val() ?? {})
            .filter((entry): entry is [string, string] => typeof entry[1] === "string" && entry[1].length > 0);

        if (tokenEntries.length === 0) {
            console.log(`No vendor FCM tokens found for canteen ${order.canteenId}`);
            return;
        }

        const title = "New Order";
        const body = `${order.studentName} placed an order for ${order.canteenName}.`;

        for (const [tokenKey, token] of tokenEntries) {
            try {
                await admin.messaging().send({
                    token,
                    data: {
                        title,
                        body,
                        type: "NEW_ORDER",
                        orderId,
                    },
                    android: {
                        priority: "high",
                    },
                });
            } catch (error: any) {
                console.error(error);
                if (error.code === "messaging/registration-token-not-registered") {
                    await admin.database()
                        .ref(`vendor_credentials/${order.canteenId}/fcmTokens/${tokenKey}`)
                        .remove();
                }
            }
        }

        console.log(`Vendor notification sent for order ${orderId}`);
    }
);

export const orderStatusChanged = onValueWritten(
    "/orders/{orderId}/status",
    
    async (event) => {
        const before = event.data?.before.val();
        const after = event.data?.after.val();

        if (before === after) return;

        const orderId = event.params.orderId;
        const orderSnapshot = await admin.database().ref(`orders/${orderId}`).once("value");
        const order = orderSnapshot.val();

        if (!order) return;

        if (after === "ACCEPTED") {
            const now = Date.now();
            const paymentDueAt = now + (10 * 60 * 1000); // 10 minutes
            await admin.database().ref(`orders/${orderId}`).update({
                status: "AWAITING_PAYMENT",
                paymentDueAt,
                "statusTimestamps/AWAITING_PAYMENT": now
            });
            return;
        }

        if (after === "REJECTED" || after === "EXPIRED" || after === "CANCELLED") {
            if (order.paymentId && order.paymentStatus === "CAPTURED") {
                await initiateRefundInternal(orderId, order);
            }
        }

        const tokenSnapshot = await admin.database().ref(`students/${order.userId}/fcmToken`).once("value");
        const token = tokenSnapshot.val();

        const isPaid = order.paymentId && order.paymentStatus === "CAPTURED";

        let title = "PlateUp";
        let body = "";

        switch (after) {
            case "AWAITING_PAYMENT":
                title = "Order Accepted 🎉";
                body = `Your order from ${order.canteenName} has been accepted. Please complete payment within 10 minutes.`;
                break;
            case "PREPARING": title = "Order Being Prepared 🧑‍🍳"; body = `Your order from ${order.canteenName} is now being prepared.`; break;
            case "READY":
                title = "Order Ready 🥪";
                body = `Your order from ${order.canteenName} is ready for pickup.`;
                // Update the combined index when status changes to READY
                await admin.database().ref(`orders/${orderId}`).update({
                    canteenId_status_pickupCode: `${order.canteenId}_READY_${order.pickupCode}`
                });
                break;
            case "COLLECTED": title = "Order Collected ✅"; body = `Enjoy your meal!`; break;
            case "COMPLETED": title = "Order Completed ✅"; body = `Enjoy your meal!`; break;
            case "REJECTED":
                title = "Order Rejected";
                body = isPaid
                    ? `Your order from ${order.canteenName} was rejected. A refund has been initiated.`
                    : `Your order from ${order.canteenName} was rejected. No payment was charged.`;
                break;
            case "EXPIRED":
                title = "Order Expired";
                body = isPaid
                    ? `The vendor did not respond in time. Your order from ${order.canteenName} has expired and a refund has been initiated.`
                    : `The vendor did not respond in time. Your order from ${order.canteenName} has expired.`;
                break;
            case "CANCELLED":
                title = "Order Cancelled";
                body = `The payment window for your order from ${order.canteenName} has expired.`;
                break;
            default: return;
        }

        const notificationRef = admin.database().ref(`notifications/${order.userId}`).push();
        const notificationId = notificationRef.key;
        if (notificationId) {
            await notificationRef.set({
                notificationId, userId: order.userId, title, message: body, timestamp: Date.now(), read: false, type: "ORDER", orderId
            });
        }

        if (token) {
            try {
                await admin.messaging().send({
                    token,
                    data: { title, body, type: "ORDER", orderId, notificationId: notificationId || "" },
                    android: { priority: "high" },
                });
            } catch (error) { console.error(error); }
        }
    }
);

async function initiateRefundInternal(orderId: string, order: any) {
    const provider = order.paymentProvider || "PAYTM";
    const credentials = await getCanteenCredentials(order.canteenId, provider);
    const paymentProvider = PaymentFactory.getProvider(provider);

    try {
        const refundId = await paymentProvider.initiateRefund(order.paymentId, order.totalAmount * 100, orderId, credentials);
        await admin.database().ref(`orders/${orderId}`).update({
            paymentStatus: "REFUNDED",
            refundId: refundId
        });
    } catch (error) {
        console.error("Refund failed:", error);
        await admin.database().ref(`orders/${orderId}`).update({ paymentStatus: "REFUND_FAILED" });
    }
}

export const expireOrders = onSchedule("every 1 minutes", async () => {
    const now = Date.now();
    const vendorTimeout = 5 * 60 * 1000;

    // Expire PLACED orders (Vendor didn't respond)
    const placedSnapshot = await admin.database().ref("orders").orderByChild("status").equalTo("PLACED").once("value");
    const placedOrders = placedSnapshot.val();
    if (placedOrders) {
        for (const orderId in placedOrders) {
            if (now - placedOrders[orderId].timestamp > vendorTimeout) {
                await admin.database().ref(`orders/${orderId}`).update({
                    status: "EXPIRED",
                    "statusTimestamps/EXPIRED": now
                });
            }
        }
    }

    // Expire AWAITING_PAYMENT orders (Student didn't pay)
    const awaitingSnapshot = await admin.database().ref("orders").orderByChild("status").equalTo("AWAITING_PAYMENT").once("value");
    const awaitingOrders = awaitingSnapshot.val();
    if (awaitingOrders) {
        for (const orderId in awaitingOrders) {
            if (now > awaitingOrders[orderId].paymentDueAt) {
                await admin.database().ref(`orders/${orderId}`).update({
                    status: "CANCELLED",
                    "statusTimestamps/CANCELLED": now
                });
            }
        }
    }
});

export const validatePickupIdentifier = onCall(async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "User must be logged in");

    const { identifier, canteenId } = request.data;
    if (!identifier || !canteenId) {
        throw new HttpsError("invalid-argument", "Missing required parameters");
    }

    const canteenSnapshot = await admin.database().ref(`canteens/${canteenId}`).once("value");
    const canteen = canteenSnapshot.val();
    if (!canteen) throw new HttpsError("not-found", "Canteen not found");

    // Brute-force protection
    await requireCanteenOwner(request.auth.uid, canteenId);
    const securityRef = admin.database().ref(`vendor_credentials/${canteenId}/security`);
    const securitySnapshot = await securityRef.once("value");
    const security = securitySnapshot.val() || {};

    if (security.failedAttempts >= 5 && Date.now() - security.lastAttempt < 5 * 60 * 1000) {
        throw new HttpsError("resource-exhausted", "Too many failed attempts. Please try again in 5 minutes.");
    }

    // Success: clear security attempts
    await securityRef.remove();

    const isCode = /^\d{6}$/.test(identifier);
    const queryField = isCode ? "pickupCode" : "pickupToken";

    const snapshot = await admin.database().ref("orders")
        .orderByChild(queryField)
        .equalTo(identifier)
        .once("value");

    const orders = snapshot.val();
    if (!orders) {
        throw new HttpsError("not-found", "Invalid or already picked up identifier");
    }

    const orderIds = Object.keys(orders);
    const matchingOrder = orders[orderIds[0]];

    if (matchingOrder.canteenId !== canteenId || matchingOrder.status !== "READY") {
        throw new HttpsError("failed-precondition", "Order is not ready for pickup at this canteen");
    }

    if (orderIds.length > 1) {
        throw new HttpsError("internal", "Duplicate pickup identifiers found");
    }

    return {
        orderId: matchingOrder.orderId,
        studentName: matchingOrder.studentName,
        items: matchingOrder.items,
        totalAmount: matchingOrder.totalAmount
    };
});

export const confirmPickup = onCall(async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "User must be logged in");

    const { orderId, canteenId } = request.data;
    if (!orderId || !canteenId) {
        throw new HttpsError("invalid-argument", "Missing required parameters");
    }

    const canteenSnapshot = await admin.database().ref(`canteens/${canteenId}`).once("value");
    const canteen = canteenSnapshot.val();
    if (!canteen) throw new HttpsError("not-found", "Canteen not found");

    // Brute-force protection
    await requireCanteenOwner(request.auth.uid, canteenId);
    const securityRef = admin.database().ref(`vendor_credentials/${canteenId}/security`);
    const securitySnapshot = await securityRef.once("value");
    const security = securitySnapshot.val() || {};

    if (security.failedAttempts >= 5 && Date.now() - security.lastAttempt < 5 * 60 * 1000) {
        throw new HttpsError("resource-exhausted", "Too many failed attempts. Please try again in 5 minutes.");
    }

    // Success: clear security attempts
    await securityRef.remove();

    const orderSnapshot = await admin.database().ref(`orders/${orderId}`).once("value");
    const order = orderSnapshot.val();

    if (!order || order.canteenId !== canteenId || order.status !== "READY") {
        throw new HttpsError("failed-precondition", "Order is not available for pickup");
    }

    const updates = {
        status: "COLLECTED",
        pickedUpAt: Date.now(),
        "statusTimestamps/COLLECTED": Date.now(),
        canteenId_status_pickupCode: `${canteenId}_COLLECTED_${order.pickupCode}`
    };

    await admin.database().ref(`orders/${orderId}`).update(updates);

    return { success: true };
});
