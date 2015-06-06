(function () {
    var app = angular.module('shocktrade');

    /**
     * Quote Services
     * @author lawrence.daniels@gmail.com
     */
    app.factory('QuoteService', function ($rootScope, $http, $log, FavoriteSymbols, MySession) {
        var quotes = {};
        var tradingHistory = {};
        var service = {
            lastSymbol: "GOOG"
        };

        function setFavorites(quotes) {
            for (var n = 0; n < quotes.length; n++) {
                quotes[n].favorite = FavoriteSymbols.isFavorite(quotes[n].symbol)
            }
            return quotes
        }

        service.autoCompleteSymbols = function (searchTerm, maxResults) {
            return $http.get('/api/quotes/autocomplete', {params: {'searchTerm': searchTerm, 'maxResults': maxResults}})
        };

        service.loadFilterQuotes = function (filter) {
            if (MySession.isAuthorized()) {
                var id = MySession.getUserID();
                return $http({
                    method: 'POST',
                    url: '/api/profile/' + id + '/quotes/filter/mini',
                    data: angular.toJson(filter)
                }).then(function (response) {
                    return setFavorites(response.data)
                })
            }
            else {
                return $http({
                    method: 'POST',
                    url: '/api/quotes/filter/mini',
                    data: angular.toJson(filter)
                }).then(function (response) {
                    return setFavorites(response.data)
                })
            }
        };

        service.loadStockQuoteList = function (symbols) {
            return $http.post("/api/quotes/list", symbols);
        };

        service.getStockQuote = function (symbol) {
            return $http.get("/api/quotes/symbol/" + symbol);
        };

        service.loadTradingHistory = function (symbol) {
            return $http.get("/api/quotes/tradingHistory/" + symbol);
        };

        service.getRiskLevel = function (symbol) {
            return $http.get("/api/quotes/riskLevel/" + symbol);
        };

        ////////////////////////////////////////////////////////////////////
        //			Market Status Functions
        ///////////////////////////////////////////////////////////////////

        service.getMarketStatus = function (callback) {
            // {"stateChanged":false,"active":false,"sysTime":1392092448795,"delay":-49848795,"start":1392042600000,"end":1392066000000}
            return $http.get("/api/tradingClock/status/0");
        };

        ////////////////////////////////////////////////////////////////////
        //			Exchange Functions
        ///////////////////////////////////////////////////////////////////

        service.getExchangeCounts = function () {
            return $http.get("/api/exchanges")
        };

        service.setExchangeState = function (id, exchange, state) {
            return state
                ? $http.put("/api/profile/" + id + "/exchange/" + exchange)
                : $http.delete("/api/profile/" + id + "/exchange/" + exchange)
        };

        ////////////////////////////////////////////////////////////////////
        //			Sector Exploration Functions
        ///////////////////////////////////////////////////////////////////

        service.loadSectorInfo = function (symbol) {
            return $http.get("/api/explore/symbol/" + symbol)
        };

        service.loadSectors = function () {
            if (MySession.isAuthorized()) {
                var id = MySession.getUserID();
                return $http.get("/api/profile/" + id + "/explore/sectors")
            }
            else {
                return $http.get("/api/explore/sectors")
            }
        };

        service.loadNAICSSectors = function () {
            if (MySession.isAuthorized()) {
                var id = MySession.getUserID();
                return $http.get("/api/profile/" + id + "/explore/naics/sectors")
            }
            else {
                return $http.get("/api/explore/naics/sectors")
            }
        };

        service.loadIndustries = function (sector) {
            if (MySession.isAuthorized()) {
                var id = MySession.getUserID();
                return $http.get("/api/profile/" + id + "/explore/industries", {params: {"sector": sector}})
            }
            else {
                return $http.get("/api/explore/industries", {params: {"sector": sector}})
            }
        };

        service.loadSubIndustries = function (sector, industry) {
            if (MySession.isAuthorized()) {
                var id = MySession.getUserID();
                return $http.get("/api/profile/" + id + "/explore/subIndustries", {
                    params: {
                        "sector": sector,
                        "industry": industry
                    }
                })
            }
            else {
                return $http.get("/api/explore/subIndustries", {params: {"sector": sector, "industry": industry}})
            }
        };

        service.loadIndustryQuotes = function (sector, industry, subIndustry) {
            if (MySession.isAuthorized()) {
                var id = MySession.getUserID();
                return $http.get("/api/profile/" + id + "/explore/quotes", {
                    params: {
                        "sector": sector,
                        "industry": industry,
                        "subIndustry": subIndustry
                    }
                })
            }
            else {
                return $http.get("/api/explore/quotes", {
                    params: {
                        "sector": sector,
                        "industry": industry,
                        "subIndustry": subIndustry
                    }
                })
            }
        };

        ////////////////////////////////////////////////////////////////////
        //			Caching Functions
        ///////////////////////////////////////////////////////////////////

        service.getFilterQuotes = function (filter) {
            return service.loadFilterQuotes(filter)
        };

        service.getPricing = function (symbols) {
            return $http.post("/api/quotes/pricing", symbols);
        };

        service.getStockQuoteList = function (symbols) {
            return service.loadStockQuoteList(symbols)
        };

        service.getTradingHistory = function (symbol) {
            if (tradingHistory[symbol] != null) return tradingHistory[symbol];
            else {
                var promise = service.loadTradingHistory(symbol);
                tradingHistory[symbol] = promise;
                return promise
            }
        };

        return service
    });

})();