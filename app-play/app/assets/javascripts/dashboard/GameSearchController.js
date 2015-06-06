(function () {
    var app = angular.module('shocktrade');

    /**
     * Game Search Controller
     * @author lawrence.daniels@gmail.com
     */
    app.controller('GameSearchController', ['$scope', '$location', '$log', '$routeParams', '$timeout', 'toaster', 'ContestService', 'InvitePlayerDialog', 'MySession', 'NewGameDialog',
        function ($scope, $location, $log, $routeParams, $timeout, toaster, ContestService, InvitePlayerDialog, MySession, NewGameDialog) {

            // public variables
            $scope.myContests = [];
            $scope.searchResults = [];
            $scope.searchTerm = "";
            $scope.selectedContest = null;
            $scope.splitScreen = false;

            // search variables
            $scope.searchOptions = {
                activeOnly: false,
                available: false,
                friendsOnly: false,
                levelCap: "1",
                levelCapAllowed: false,
                perksAllowed: false,
                robotsAllowed: false
            };

            ///////////////////////////////////////////////////////////////////////////
            //          Public Functions
            ///////////////////////////////////////////////////////////////////////////

            $scope.initGames = function () {
                $scope.contestSearch($scope.searchOptions);
            };

            $scope.enterGame = function (contest) {
                MySession.setContest(contest);
                $location.path("/dashboard/" + contest.OID());
            };

            $scope.invitePlayerPopup = function (contest, playerID) {
                var participant = ContestService.findPlayerByID(contest, playerID);
                if(participant) {
                    InvitePlayerDialog.popup($scope, participant);
                }
                else {
                    toaster.pop('error', 'You must join tghe game to use this feature', null);
                }
            };

            $scope.getAvailableCount = function () {
                var count = 0;
                angular.forEach($scope.searchResults, function (r) {
                    if (r.status === 'ACTIVE') count++;
                });
                return count;
            };

            $scope.getAvailableSlots = function (contest, row) { // 0,1
                var slots = [];
                if (contest) {
                    var participants = contest.participants || [];
                    var start = row * 8; // 0=0, 1=8, 2=16
                    var end = row * 8 + 8; // 0=7, 1=8, 2=15
                    for (var n = start; n < end; n++) {
                        slots.push((n < participants.length) ? participants[n] : null);
                    }
                }
                return slots;
            };

            $scope.contestSearch = function (searchOptions) {
                $scope.startLoading();

                ContestService.findContests(searchOptions).then(
                    function (contests) {
                        $scope.searchResults = contests;
                        $scope.stopLoading();
                    },
                    function (err) {
                        toaster.pop('error', 'Error!', "Failed to execute Contest Search");
                        $scope.stopLoading();
                    });
            };

            $scope.getSearchResults = function (searchTerm) {
                var term = (searchTerm || "").trim().toLowerCase();
                if (term.length === 0) return $scope.searchResults;
                else {
                    // $log.info("searchTerm = " + searchTerm);
                    return $scope.searchResults.filter(function (c) {
                        return c.name.toLowerCase().indexOf(term) != -1;
                    });
                }
            };

            $scope.contestStatusClass = function (contest) {
                if (contest == null) return "";
                else if (contest.status == 'ACTIVE') return "positive";
                else if (contest.status == 'CLOSED') return "negative";
                else return "";
            };

            $scope.getStatusClass = function (c, maxPlayers) {
                if (!c) return null;
                else {
                    var playerCount = ((c && c.participants) || []).length;
                    if (playerCount + 1 < maxPlayers) return "positive";
                    else if (playerCount + 1 === maxPlayers) return "warning";
                    else if (playerCount >= maxPlayers) return "negative";
                    else if (c.status === 'ACTIVE') return 'positive';
                    else if (c.status === 'CLOSED') return 'negative';
                    else return "null";
                }
            };

            $scope.trophy = function (place) {
                switch (place) {
                    case '1st':
                        return "contests/gold.png";
                    case '2nd':
                        return "contests/silver.png";
                    case '3rd':
                        return "contests/bronze.png";
                    default:
                        return "status/transparent.png";
                }
            };

            ///////////////////////////////////////////////////////////////////////////
            //          My Contest Functions
            ///////////////////////////////////////////////////////////////////////////

            $scope.getMyContests = function () {
                return $scope.myContests;
            };

            $scope.getMyRankings = function (contest) {
                if (!contest) return {};
                else {
                    var rankings = ContestService.getPlayerRankings(contest, MySession.getUserName());
                    return rankings.player;
                }
            };

            $scope.newGamePopup = function () {
                NewGameDialog.popup({
                    "success": function (contest) {
                        $scope.myContests.push(contest);
                    },
                    "error": function(err) {
                        toaster.pop('error', 'Failed to create game', null);
                    }
                });
            };

            function loadMyContests() {
                var userID = MySession.getUserID();
                if (!userID) return [];
                else {
                    $scope.myContests = [];

                    $log.info("Loading 'My Contests'...");
                    ContestService.getContestsByPlayerID(userID)
                        .success(function (contests) {
                            $log.info("Loaded " + contests.length + " contest(s)");
                            $scope.myContests = contests;
                        })
                        .error(function (response) {
                            toaster.pop('error', "Failed to load 'My Contests'", null)
                        });
                }
            }

            ///////////////////////////////////////////////////////////////////////////
            //          Contest Selection Functions
            ///////////////////////////////////////////////////////////////////////////

            $scope.isSplitScreen = function () {
                return $scope.splitScreen && ($scope.selectedContest != null);
            };

            $scope.selectContest = function (contest) {
                $log.info("Selecting contest '" + contest.name + "' (" + contest.OID() + ")");
                $scope.selectedContest = contest;
                $scope.splitScreen = true;

                if (!contest.rankings) {
                    ContestService.getPlayerRankings(contest, MySession.getUserName());
                }
            };

            $scope.toggleSplitScreen = function () {
                $scope.splitScreen = false;
            };

            function isContestSelected(contestId) {
                return $scope.selectedContest && ($scope.selectedContest.OID() === contestId);
            }

            ///////////////////////////////////////////////////////////////////////////
            //          Contest Management Functions
            ///////////////////////////////////////////////////////////////////////////

            $scope.containsPlayer = function (contest, userProfile) {
                return userProfile.id && ContestService.findPlayerByID(contest, userProfile.OID()) != null;
            };

            $scope.isContestOwner = function (contest) {
                return contest && contest.creator.name === MySession.getUserName();
            };

            $scope.isDeletable = function (contest) {
                return $scope.isContestOwner(contest) && (!contest.startTime || contest.participants.length == 1);
            };

            $scope.isParticipant = function (contest) {
                var id = MySession.getUserID();
                return id && ContestService.findPlayerByID(contest, id) != null;
            };

            $scope.deleteContest = function (contest) {
                contest.deleting = true;
                $log.info("Deleting contest " + contest.name + "...");
                ContestService.deleteContest(contest.OID())
                    .success(function (response) {
                        //removeContestFromList(contest.OID());
                        $scope.contestSearch($scope.searchOptions);

                        $timeout(function () {
                            contest.deleting = false;
                        }, 500);
                    })
                    .error(function (err) {
                        toaster.pop('error', 'Error!', "Failed to delete contest");
                        $log.error("An error occurred while deleting the contest");
                        $timeout(function () {
                            contest.deleting = false;
                        }, 500);
                    });
            };

            $scope.isJoinable = function (contest) {
                return MySession.authenticated && contest && !contest.invitationOnly && !$scope.isContestOwner(contest) && !$scope.isParticipant(contest);
            };

            $scope.joinContest = function (contest) {
                contest.joining = true;
                ContestService.joinContest(contest.OID(), {
                    player: {
                        "id": MySession.getUserID(),
                        "name": MySession.getUserName(),
                        "facebookID": MySession.fbUserID
                    }
                }).success(function (contest) {
                    if (!contest) {
                        toaster.pop('error', 'Error!', "Failed to join game");
                        $log.error("Returned contest was null")
                    }
                    else if (contest.error) {
                        toaster.pop('error', 'Error!', contest.error);
                        $log.error(contest.error);
                    }
                    else {
                        $scope.contest = contest;
                        MySession.setContest(contest);
                        //MySession.deduct(contest.startingBalance);
                        //updateWithRankings(MySession.getUserName(), contest)
                    }

                    $timeout(function () {
                        contest.joining = false;
                    }, 500);

                }).error(function (err) {
                    toaster.pop('error', 'Error!', "Failed to join contest");
                    $log.error("An error occurred while joining the contest");
                    $timeout(function () {
                        contest.joining = false;
                    }, 500);
                });
            };

            $scope.joinedParticipant = function (contest, userProfile) {
                return $scope.containsPlayer(contest, userProfile);
            };

            $scope.quitContest = function (contest) {
                contest.quitting = true;
                ContestService.quitContest(contest.OID(), MySession.getUserID())
                    .success(function (updatedContest) {
                        if (!updatedContest.error) {
                            $scope.contest = updatedContest;
                            MySession.setContest(updatedContest);
                        }
                        else {
                            $log.error("error = " + updatedContest.error);
                            toaster.pop('error', 'Error!', "Unable to process your quit command at this time.")
                        }

                        $timeout(function () {
                            contest.quitting = false;
                        }, 500);
                    })
                    .error(function (err) {
                        $log.error("An error occurred while joining the contest");
                        $timeout(function () {
                            contest.quitting = false;
                        }, 500);
                    });
            };

            $scope.startContest = function (contest) {
                contest.starting = true;
                ContestService.startContest(contest.OID())
                    .success(function (contest) {
                        if (contest.error) {
                            toaster.pop('error', 'Error!', contest.error);
                            $log.error(contest.error);
                        }

                        $timeout(function () {
                            contest.starting = false;
                        }, 500);
                    })
                    .error(function (err) {
                        $log.error("An error occurred while starting the contest");
                        $timeout(function () {
                            contest.starting = false;
                        }, 500);
                    });
            };

            //////////////////////////////////////////////////////////////////////
            //              Style/CSS Functions
            //////////////////////////////////////////////////////////////////////

            $scope.getSelectionClass = function (c) {
                return $scope.selectedContest && ($scope.selectedContest.OID() === c.OID()) ? 'selected' : (c.status === 'ACTIVE' ? '' : 'null')
            };

            //////////////////////////////////////////////////////////////////////
            //              Broadcast Event Listeners
            //////////////////////////////////////////////////////////////////////

            function indexOfContest(contestId) {
                for (var n = 0; n < $scope.searchResults.length; n++) {
                    var contest = $scope.searchResults[n];
                    if (contest.OID() === contestId) return n;
                }
                return -1;
            }

            function updateContestInList(searchResults, contestId) {
                for (var n = 0; n < searchResults.length; n++) {
                    if (searchResults[n].OID() === contestId) {
                        var index = n;
                        ContestService.getContestByID(contestId)
                            .success(function (loadedContest) {
                                searchResults[index] = loadedContest;
                            })
                            .error(function (xhr, status, error) {
                                $log.error("Error selecting feed: " + xhr.error);
                                toaster.pop('error', 'Error!', "Error loading game");
                            });
                    }
                }
            }

            function removeContestFromList(searchResults, contestId) {
                var index = indexOfContest(contestId);
                if (index != -1) {
                    $log.info("Removed contest " + contestId + " from the list...");
                    searchResults.splice(index, 1);
                }

                if ($scope.selectedContest && $scope.selectedContest.OID() === contestId) {
                    $scope.selectedContest = null;
                }
            }

            ///////////////////////////////////////////////////////////////////////////
            //          Event Listeners
            ///////////////////////////////////////////////////////////////////////////

            /**
             * Listen for contest creation events
             */
            $scope.$on("contest_created", function (event, contest) {
                $log.info("New contest created '" + contest.name + "'");
                $scope.searchResults.push(contest);
                loadMyContests();
                MySession.refresh();
            });

            /**
             * Listen for contest deletion events
             */
            $scope.$on("contest_deleted", function (event, contest) {
                $log.info("Contest '" + contest.name + "' deleted");
                $scope.selectedContest = null;
                $scope.searchResults.push(contest);
                loadMyContests();
                MySession.refresh();
            });

            /**
             * Listen for contest update events
             */
            $scope.$on("contest_updated", function (event, contest) {
                $log.info("Contest '" + contest.name + "' updated");
                var contestId = contest.OID();

                // update the contest in our search results
                updateContestInList($scope.searchResults, contestId);
                updateContestInList($scope.myContests, contestId);

                // make sure we're pointing at the updated contest
                if (isContestSelected(contestId)) {
                    $scope.selectedContest = contest;
                }
            });

            $scope.$watch("MySession.userProfile._id", function () {
                loadMyContests();
            });

        }]);

})();