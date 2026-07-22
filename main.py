import asyncio
import json
import random
import socket
import time
from fastapi import FastAPI, WebSocket
from fastapi.middleware.cors import CORSMiddleware
from uvicorn import run

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

INDIAN_STOCKS = ["RELIANCE", "TCS", "INFY", "HDFCBANK"]
stock_database = {
    "RELIANCE": [2500.0] * 10,
    "TCS": [3500.0] * 10,
    "INFY": [1420.0] * 10,
    "HDFCBANK": [1680.0] * 10
}

# Track cooldowns so the automated AI engine executes at a readable pace
last_ai_trade_time = {ticker: 0 for ticker in INDIAN_STOCKS}
AI_COOLDOWN_SECONDS = 3.0 

def run_realtime_ai_agent(ticker: str, price_history: list) -> dict:
    if len(price_history) < 5:
        return {"signal": "HOLD", "action": "Synchronizing Pipeline Frame..."}
    recent = price_history[-5:]
    gradient = recent[-1] - recent[0]
    
    # Mathematical thresholds tracking sharp trend breakouts
    if gradient > 12.0:
        return {"signal": "SELL", "action": f"Overbought anomaly on {ticker}. Mean-reversion vector confirmed."}
    elif gradient < -12.0:
        return {"signal": "BUY", "action": f"Oversold momentum dip on {ticker}. Institutional block entry zone."}
    return {"signal": "HOLD", "action": f"Consolidating within normal deviations for {ticker}."}

def dispatch_to_java_matching_engine(order_payload: dict):
    try:
        client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        client.connect(('127.0.0.1', 9999))
        csv_payload = f"{order_payload['ticker']},{order_payload['side']},{order_payload['qty']},{order_payload['price']}\n"
        client.send(csv_payload.encode())
        client.close()
        print(f"[IPC BRIDGE] Successfully dispatched order to Java Engine: {csv_payload.strip()}")
    except Exception as e:
        print(f"[IPC BRIDGE WARNING] Java Engine offline on port 9999: {e}")

@app.websocket("/ws/market-feed")
async def market_feed_endpoint(websocket: WebSocket):
    await websocket.accept()
    print("[WS COMPONENT] Frontend connection handshake completed.")
    
    async def listen_for_client_orders():
        try:
            while True:
                data = await websocket.receive_text()
                parsed_data = json.loads(data)
                if parsed_data.get("action") == "SUBMIT_ORDER":
                    print(f"[WS COMPONENT] Intercepted manual user interface trade: {parsed_data}")
                    dispatch_to_java_matching_engine(parsed_data)
        except Exception:
            pass

    asyncio.create_task(listen_for_client_orders())

    try:
        while True:
            for ticker in INDIAN_STOCKS:
                current_price = stock_database[ticker][-1]
                price_delta = random.uniform(-15.0, 15.5)
                new_price = round(max(10.0, current_price + price_delta), 2)
                stock_database[ticker].append(new_price)
                
                if len(stock_database[ticker]) > 50:
                    stock_database[ticker].pop(0)
                    
                ai_analysis = run_realtime_ai_agent(ticker, stock_database[ticker])
                
                payload = {
                    "symbol": ticker,
                    "live_price": new_price,
                    "ai_forecast": ai_analysis["signal"],
                    "ai_action_plan": ai_analysis["action"]
                }
                
                # AUTOMATED QUANT STRATEGY WITH COOLDOWN PROTECTION
                if payload["ai_forecast"] in ["BUY", "SELL"]:
                    current_time = time.time()
                    if current_time - last_ai_trade_time[ticker] >= AI_COOLDOWN_SECONDS:
                        auto_order = {"ticker": ticker, "side": payload["ai_forecast"], "qty": 100, "price": new_price}
                        dispatch_to_java_matching_engine(auto_order)
                        last_ai_trade_time[ticker] = current_time

                await websocket.send_text(json.dumps(payload))
                await asyncio.sleep(0.15)
    except Exception as e:
        print(f"[WS COMPONENT] Pipeline connection dropped: {e}")

if __name__ == "__main__":
    run(app, host="127.0.0.1", port=8000)