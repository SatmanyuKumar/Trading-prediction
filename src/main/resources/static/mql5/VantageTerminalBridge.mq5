//+------------------------------------------------------------------+
//|                                     VantageTerminalBridge.mq5   |
//|                 Pro SMC Trading Terminal Live Bridge for MT5     |
//|            1:1 Real-Time Position Mirror with Exact SL & TP      |
//+------------------------------------------------------------------+
#property copyright "Pro SMC Trading Terminal"
#property link      "http://localhost:8080"
#property version   "1.40"
#property strict

#include <Trade\Trade.mqh>
CTrade trade;

input string WebUrl1 = "http://127.0.0.1:8080"; // Primary Web URL
input string WebUrl2 = "http://localhost:8080"; // Fallback Web URL
input string SocketHost = "127.0.0.1";           // Socket IP
input int    SocketPort = 8222;                 // Socket Port
input int    SyncTimerSeconds = 1;              // Timer Seconds

int socketHandle = INVALID_HANDLE;

//+------------------------------------------------------------------+
//| Expert initialization function                                   |
//+------------------------------------------------------------------+
int OnInit()
{
   Print(">>> Initializing Pro SMC Vantage MT5 Bridge v1.40 (SL/TP Enabled) <<<");
   ConnectSocket();
   SendSyncWebRequest();
   EventSetTimer(SyncTimerSeconds);
   return(INIT_SUCCEEDED);
}

//+------------------------------------------------------------------+
//| Expert deinitialization function                                 |
//+------------------------------------------------------------------+
void OnDeinit(const int reason)
{
   EventKillTimer();
   if(socketHandle != INVALID_HANDLE)
   {
      SocketClose(socketHandle);
      socketHandle = INVALID_HANDLE;
   }
}

//+------------------------------------------------------------------+
//| Connect Socket                                                   |
//+------------------------------------------------------------------+
void ConnectSocket()
{
   if(socketHandle != INVALID_HANDLE && SocketIsConnected(socketHandle)) return;
   
   socketHandle = SocketCreate();
   if(socketHandle != INVALID_HANDLE)
   {
      if(SocketConnect(socketHandle, SocketHost, SocketPort, 400))
      {
         Print(">>> [SOCKET SUCCESS] Connected to Spring Boot on port ", SocketPort);
      }
      else
      {
         SocketClose(socketHandle);
         socketHandle = INVALID_HANDLE;
      }
   }
}

//+------------------------------------------------------------------+
//| Timer function                                                   |
//+------------------------------------------------------------------+
void OnTimer()
{
   ConnectSocket();
   
   if(socketHandle != INVALID_HANDLE && SocketIsConnected(socketHandle))
   {
      SendSocketSync();
      CheckSocketOrders();
   }
   
   SendSyncWebRequest();
   PollOrdersWebRequest();
}

//+------------------------------------------------------------------+
//| Helper: Get All Open Positions JSON                              |
//+------------------------------------------------------------------+
string GetPositionsJson()
{
   int total = PositionsTotal();
   string json = "[";
   int count = 0;
   
   for(int i = 0; i < total; i++)
   {
      ulong ticket = PositionGetTicket(i);
      if(ticket > 0 && PositionSelectByTicket(ticket))
      {
         string sym = PositionGetString(POSITION_SYMBOL);
         ENUM_POSITION_TYPE type = (ENUM_POSITION_TYPE)PositionGetInteger(POSITION_TYPE);
         double vol = PositionGetDouble(POSITION_VOLUME);
         double openPrice = PositionGetDouble(POSITION_PRICE_OPEN);
         double currentPrice = PositionGetDouble(POSITION_PRICE_CURRENT);
         double sl = PositionGetDouble(POSITION_SL);
         double tp = PositionGetDouble(POSITION_TP);
         double profit = PositionGetDouble(POSITION_PROFIT);
         long time = PositionGetInteger(POSITION_TIME);
         
         if(count > 0) json += ",";
         json += StringFormat(
            "{\"ticket\":\"%I64u\",\"symbol\":\"%s\",\"type\":\"%s\",\"lots\":%.2f,\"entryPrice\":%.2f,\"currentPrice\":%.2f,\"sl\":%.2f,\"tp\":%.2f,\"pnl\":%.2f,\"time\":%I64d}",
            ticket, sym, (type == POSITION_TYPE_BUY ? "BUY" : "SELL"), vol, openPrice, currentPrice, sl, tp, profit, time
         );
         count++;
      }
   }
   json += "]";
   return json;
}

//+------------------------------------------------------------------+
//| Helper: Full Account & Positions Payload                         |
//+------------------------------------------------------------------+
string GetFullPayloadJson()
{
   long login = AccountInfoInteger(ACCOUNT_LOGIN);
   string server = AccountInfoString(ACCOUNT_SERVER);
   double balance = AccountInfoDouble(ACCOUNT_BALANCE);
   double equity = AccountInfoDouble(ACCOUNT_EQUITY);
   double margin = AccountInfoDouble(ACCOUNT_MARGIN);
   double freeMargin = AccountInfoDouble(ACCOUNT_MARGIN_FREE);
   long leverage = AccountInfoInteger(ACCOUNT_LEVERAGE);

   return StringFormat(
      "{\"type\":\"ACCOUNT_SYNC\",\"account\":\"%I64d\",\"server\":\"%s\",\"balance\":%.2f,\"equity\":%.2f,\"margin\":%.2f,\"freeMargin\":%.2f,\"leverage\":%I64d,\"positions\":%s}",
      login, server, balance, equity, margin, freeMargin, leverage, GetPositionsJson()
   );
}

//+------------------------------------------------------------------+
//| Send Sync via Socket                                             |
//+------------------------------------------------------------------+
void SendSocketSync()
{
   string json = GetFullPayloadJson();
   uchar data[];
   StringToCharArray(json + "\n", data, 0, WHOLE_ARRAY, CP_UTF8);
   SocketSend(socketHandle, data, ArraySize(data) - 1);
}

//+------------------------------------------------------------------+
//| Check Socket Orders                                              |
//+------------------------------------------------------------------+
void CheckSocketOrders()
{
   uint readable = SocketIsReadable(socketHandle);
   if(readable > 0)
   {
      uchar buffer[];
      int len = SocketRead(socketHandle, buffer, readable, 100);
      if(len > 0)
      {
         string msg = CharArrayToString(buffer, 0, len, CP_UTF8);
         HandleIncomingCommand(msg);
      }
   }
}

//+------------------------------------------------------------------+
//| Send Sync via WebRequest                                         |
//+------------------------------------------------------------------+
void SendSyncWebRequest()
{
   string json = GetFullPayloadJson();
   char postData[];
   char resultData[];
   string resultHeaders;
   StringToCharArray(json, postData, 0, WHOLE_ARRAY, CP_UTF8);

   int res = WebRequest("POST", WebUrl1 + "/api/vantage/webhook", "Content-Type: application/json\r\n", 400, postData, resultData, resultHeaders);
   if(res != 200)
   {
      WebRequest("POST", WebUrl2 + "/api/vantage/webhook", "Content-Type: application/json\r\n", 400, postData, resultData, resultHeaders);
   }
}

//+------------------------------------------------------------------+
//| Poll Orders via WebRequest                                       |
//+------------------------------------------------------------------+
void PollOrdersWebRequest()
{
   char postData[];
   char resultData[];
   string resultHeaders;

   int res = WebRequest("GET", WebUrl1 + "/api/vantage/pending-orders", "", 400, postData, resultData, resultHeaders);
   if(res != 200)
   {
      res = WebRequest("GET", WebUrl2 + "/api/vantage/pending-orders", "", 400, postData, resultData, resultHeaders);
   }

   if(res == 200 && ArraySize(resultData) > 5)
   {
      string responseJson = CharArrayToString(resultData, 0, WHOLE_ARRAY, CP_UTF8);
      HandleIncomingCommand(responseJson);
   }
}

//+------------------------------------------------------------------+
//| Handle Orders & Close Requests                                   |
//+------------------------------------------------------------------+
void HandleIncomingCommand(string json)
{
   if(StringFind(json, "OPEN_ORDER") >= 0)
   {
      Print(">>> [ORDER COMMAND WITH SL/TP] ", json);
      ExecuteOpenOrder(json);
   }
   else if(StringFind(json, "CLOSE_ORDER") >= 0)
   {
      Print(">>> [CLOSE COMMAND] ", json);
      ExecuteCloseOrder(json);
   }
}

//+------------------------------------------------------------------+
//| Execute Open Order with Real SL and TP                           |
//+------------------------------------------------------------------+
void ExecuteOpenOrder(string json)
{
   string targetSymbol = _Symbol;
   
   if(StringFind(json, "EURUSD") >= 0) targetSymbol = "EURUSD";
   else if(StringFind(json, "GBPUSD") >= 0) targetSymbol = "GBPUSD";
   else if(StringFind(json, "USDJPY") >= 0) targetSymbol = "USDJPY";
   else if(StringFind(json, "BTCUSD") >= 0) targetSymbol = "BTCUSD";
   else if(StringFind(json, "XAUUSD") >= 0) targetSymbol = "XAUUSD";

   if(!SymbolInfoInteger(targetSymbol, SYMBOL_SELECT))
   {
      if(SymbolInfoInteger(targetSymbol + "+", SYMBOL_SELECT)) targetSymbol = targetSymbol + "+";
      else if(SymbolInfoInteger(targetSymbol + ".raw", SYMBOL_SELECT)) targetSymbol = targetSymbol + ".raw";
      else if(SymbolInfoInteger(targetSymbol + "m", SYMBOL_SELECT)) targetSymbol = targetSymbol + "m";
   }

   bool isBuy = (StringFind(json, "\"type\":\"BUY\"") >= 0);
   double lots = 1.0;
   
   int lotPos = StringFind(json, "\"lots\":");
   if(lotPos >= 0)
   {
      string sub = StringSubstr(json, lotPos + 7, 6);
      lots = StringToDouble(sub);
      if(lots <= 0.0) lots = 1.0;
   }

   // Extract SL
   double sl = 0.0;
   int slPos = StringFind(json, "\"sl\":");
   if(slPos >= 0)
   {
      string sub = StringSubstr(json, slPos + 5, 12);
      int comma = StringFind(sub, ",");
      int brace = StringFind(sub, "}");
      if(comma > 0) sub = StringSubstr(sub, 0, comma);
      else if(brace > 0) sub = StringSubstr(sub, 0, brace);
      sl = StringToDouble(sub);
   }

   // Extract TP
   double tp = 0.0;
   int tpPos = StringFind(json, "\"tp\":");
   if(tpPos >= 0)
   {
      string sub = StringSubstr(json, tpPos + 5, 12);
      int comma = StringFind(sub, ",");
      int brace = StringFind(sub, "}");
      if(comma > 0) sub = StringSubstr(sub, 0, comma);
      else if(brace > 0) sub = StringSubstr(sub, 0, brace);
      tp = StringToDouble(sub);
   }

   double price = isBuy ? SymbolInfoDouble(targetSymbol, SYMBOL_ASK) : SymbolInfoDouble(targetSymbol, SYMBOL_BID);
   
   trade.SetExpertMagicNumber(998877);
   trade.SetDeviationInPoints(50);
   
   Print(">>> PLACING SL/TP ORDER: ", isBuy ? "BUY" : "SELL", " ", lots, " Lots @ ", price, " SL=", sl, " TP=", tp);
   
   if(isBuy)
   {
      trade.Buy(lots, targetSymbol, price, sl, tp, "ProSMC_AutoTrade");
   }
   else
   {
      trade.Sell(lots, targetSymbol, price, sl, tp, "ProSMC_AutoTrade");
   }
   
   SendSyncWebRequest();
}

//+------------------------------------------------------------------+
//| Execute Close Order                                              |
//+------------------------------------------------------------------+
void ExecuteCloseOrder(string json)
{
   int ticketPos = StringFind(json, "\"ticket\":");
   if(ticketPos >= 0)
   {
      string sub = StringSubstr(json, ticketPos + 10, 15);
      int endQuote = StringFind(sub, "\"");
      if(endQuote > 0) sub = StringSubstr(sub, 0, endQuote);
      
      ulong ticket = (ulong)StringToInteger(sub);
      if(ticket > 0)
      {
         trade.PositionClose(ticket);
         Print(">>> [POSITION CLOSED IN MT5] Ticket: ", ticket);
      }
   }
   SendSyncWebRequest();
}
