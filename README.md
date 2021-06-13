---

---

<h1 id="raffle-xy">Raffle X/Y</h1>
<h2 id="list">List</h2>
<p><strong>1. Introduction<br>
2. Starting Raffle<br>
3. Phase 1. Ticket Selling<br>
4. Phase 2. Raffle Result<br>
5. Phase 3. Finding Winner<br>
6. More Technical Details<br>
7. Screen Shots</strong></p>
<h2 id="introduction">Introduction</h2>
<p>A raffle is a competition that participants join via buying tickets, and in the end, a participant with the winner’s ticket wins the whole collected money. In a 50/50 scenario, 50% of the collected money is donated to the charity while the winner participant wins the rest.</p>
<p>We implemented the raffle 50/50 dApp on top of the ergo blockchain. Despite the name of the raffle, this implementation is flexible enough to pay the charity and winner any predefined percentage (we take 10% of the money as the service fee). As we want to make sure that we will raise enough donations to the charity within a deadline, we define these two values (i.e. <code>deadline</code> and <code>minToRaise</code>) at the beginning. If the raffle could collect enough budget in that period, we claim that the raffle was successful, and then charity, winner, and service will charge accordingly. Otherwise, the raffle is unsuccessful, and it will pay back all the participants the sold tickets prices.</p>
<p>Although the raffle with deadline and minimum raising goal is somehow similar to the crowdfunding, we didn’t use the presented idea for crowdfunding on the whitepaper, since it has some problems. For example, since it collects all inputs in one transaction and it’s limited to inputs and outputs of one transaction, it can not handle a large number of participants. It also didn’t have details on special cases and participation policies.</p>
<p>In the following, we present our idea that is based on a self-replicating box that collects the participant funds. Then based on the amount of total raised fund, decides to refund the participants or announce the winner.</p>
<h2 id="starting-raffle">Starting Raffle</h2>
<p>At the beginning, to start a new raffle, you just need to set some parameters such as charity and winner percentage, charity address, deadline, minimum total raising, and ticket price. Then pay the minimum fee needed to make a new box. Finally, you can spread the raffle box and inspire other people to join your raffle. The raffle lifetime has three phases that we will explain later.</p>
<p>Since you need to pay the raffle start fee to start a raffle, we designed a script that can be specialized based on the raffle setting. After receiving the raffle parameters we compile the script and give you an address; you need to pay the start fee to that address in order to start the raffle. The designed script only can be spent to make a raffle box based on your inputs.</p>
<p><img src="https://github.com/NazeriMahdi2001/Raffle-Doc/blob/master/Images/initialization.png?raw=true" alt="enter image description here"></p>
<h2 id="phase-1.-ticket-selling">Phase 1. Ticket Selling</h2>
<p>In the first phase, the raffle starts with selling the tickets. The ticket price is fixed, and the raffle box sells a specific token that is issued for this purpose(the raffle token). The raffle box can be spent in this phase only if the first output of the transaction is a self-replicated box and this new raffle box Erg values increased based on the number of sold raffle tokens*. The second output of the mentioned transaction is the ticket that can win the competition proportional to its worth (number of owning raffle tokens).</p>
<p>Each participant will pay <code>2 * minFee</code> more Ergs than the ticket worth to join the raffle, and each resulting ticket will have <code>minFee</code> Ergs and <code>(payedAmount - 2 * minFee) / ticketPrice</code> raffle tokens. The ticket box generated in each round has a unique range specified by its start point and length. The range started from the value in the <code>R4</code> register and its length is in the <code>R5</code> register (it is also can be determined by the number of owned tokens).</p>
<p>Like the raffle start fee, we designed a script that can be used to join the raffle. Each participant gets his address by specifying his public key. Then he can pay to script any amount he wants. The box protected with this script can only be spent to join the raffle and the script ensures that the resulting ticket belongs to the public key specified in the first step.</p>
<p>It worth mentioning that although a user can join the raffle by paying to the designed script, she can also make a transaction herself. Since the <em>pay to script address</em> increases the exchanging steps, it costs further fees; thus, by making the transaction herself, she can decrease the fees (<code>2 * minFee</code> instead of <code>3 * minFee</code>).</p>
<p><img src="https://github.com/NazeriMahdi2001/Raffle-Doc/blob/master/Images/phase1.png?raw=true" alt="enter image description here"></p>
<h2 id="phase-2.-raffle-result">Phase 2. Raffle Result</h2>
<p>After passing the deadline, the raffle enters phase 2. In this phase, the raffle box won’t sell any other tokens and checks whether or not it raised enough funds within the deadline.</p>
<p>Suppose the total raised money is greater than the <code>minToRaise</code> specified at the beginning. In that case, the raffle is successful, and it creates three new boxes out of the raised money with the raffle setting (percentages and addresses). The charity box and service fee box are simple boxes protected with the related public keys, but the winner box is a little different, and we will start phase 3 to find and pay the winner.</p>
<p><img src="https://github.com/NazeriMahdi2001/Raffle-Doc/blob/master/Images/successful.png?raw=true" alt="enter image description here"></p>
<p>Finally, if the raffle couldn’t reach the raise goal, the raffle box turns into a box that can be spent by any person who has a ticket related to this raffle (a ticket that has a raffle token). Like the first phase, in a spending transaction raffle box makes a self-replicate of itself and makes sure that the value differs between these two boxes proportional to the paid back tokens.</p>
<p>The difference between the two raffle boxes belongs to the ticket owner and can be placed in any output box he wishes.</p>
<p>The ticket script is designed such that it can be spent by anyone, only if the reception of the value is the ticket owner. The ticket can be spent in two conditions; if the raffle is unsuccessful and we are in refunding phase or the raffle is successful and the ticket is spent in order to get the winner reward or finding the winner.</p>
<p><img src="https://github.com/NazeriMahdi2001/Raffle-Doc/blob/master/Images/refund.png?raw=true" alt="Refund.png"></p>
<h2 id="phase-3.-finding-winner">Phase 3. Finding Winner</h2>
<p>As mentioned above, if the raffle is completed successfully, it will create a new box named winner box. We had different ideas for this step since it’s the most important point in the raffle protocol. Finally, we decided to implement one of the ideas but we briefly describe our ideas here.</p>
<h3 id="pseudo-random-number">Pseudo-Random Number</h3>
<p>With any approach, we need to have a random number to determine the winner. Since we don’t have access to a random number generator on the ledger, we should use the available pseudo-random numbers on the chain.</p>
<p>1- <strong>Block Id</strong></p>
<p>For example, the previous block id is a number that can be used as the random number since it’s out of the control of the participants. As the PoW puzzle influences the block id, a participant can not jointly solve the PoW puzzle and optimize the generated id to influence the raffle result. If a party owns x percent of total computation power and he mines the last block related to this raffle, we estimate that the probability of his sabotage raffle result influence is less than x^4. (Since they are Poisson random variables you can write the mathematical formulas with Poisson process relations). However, this value is not that small.</p>
<p>2- <strong>Oracle Box Id</strong></p>
<p>Another random number that can be used here is the Oracle Box Ids. Since this Id updates frequently in specified periods, and it’s not controllable by the participants can be used as the random number that identifies the winner.</p>
<p>3- <strong>Random Number with Participation</strong></p>
<p>Another way to find a truly random number is by asking each participant to introduce a random number and aggregate all these numbers. It can be done by asking the participants to commit the hash of their random numbers in the first phase, and reveal it in the final phase.</p>
<p>Since the previous block id can be influenced by the miners with small probability and the last method increases the costs and complexity we decided to use the Oracle Box Id as the pseudo-random number in this phase.</p>
<h3 id="selection-algorithm">Selection Algorithm</h3>
<p>With owning a pseudo-random number we need to define a selection algorithm to select the winner. We studied algorithms that can support weighted chances; since one may buy many tokens with one ticket and he should have more probability of winning the raffle. As already discussed, each ticket has a unique range hardcoded in its registers; then, we can use these unique numbers to find the winner.</p>
<p>1- <strong>Mod</strong></p>
<p>A simple approach for finding the winner is to compute the remainder of the division of the random number by the total number of sold tokens. Then we can check that this number is within the range of which ticket. In other words, the winner box can be spent by the one who provides a ticket that the winning number is within its valid range.</p>
<p>2- <strong>Minimum Hash</strong></p>
<p>In this algorithm, we define the winner as “the participant who has the minimum <code>hash(randomSeed | nonce)</code>” where the <code>|</code> means concatenation and the <code>nonce</code> is a number in the valid range of the participant’s ticket.</p>
<p>To find the winner, we are not required to compute all these hashes, and participants are responsible for running the related computations themselves. To aggregate the results, each participant passes the minimum value he could compute with its ticket range to the winner box. The winner box updates the winner if the given value is less than the value it already has.</p>
<p>In other words, the winner box is a self-replicating box before the deadline, and it is protected with a script that ensures only a participant with a hash value less than the current winner hash value can spend it. Then the participant can change the winner information within the box. Finally, after the deadline, the actual winner can spend the whole amount.</p>
<p>The winner box should validate the computation done by each participant; thus, as well as the ticket the nonce that created the least hash value for each participant passed to the winner box via a context variable.</p>
<p>3- <strong>Sortition Algorithm</strong></p>
<p>There are some algorithms that use the secret key of the participant to claim that is he the winner or not. A famous example of these algorithms is Algorand sortition algorithm. Although this is a probabilistic algorithm and may have more than one winner we can use this algorithm to mitigate some attack vectors, especially the one we presented in the block id random number approach. We can use the previous idea along with the sortition algorithm to solve the problem of more than one winner.</p>
<p>After all, we decided to implement the first approach because:</p>
<p>1- It has the least network costs and fees, and it’s simplest to implement in this short period!</p>
<p>2- Minimum hash idea increases the randomness of the result, but this growth is negligible compared to the network computation powers.</p>
<p>3- Minimum hash idea, needs the client full control over his ticket since anyone else may burn his ticket by providing an improper context number.</p>
<p>4- The sortition algorithm needs the client secret key in computations, which is not a desirable requirement in a dApp setting.</p>
<p><img src="https://github.com/NazeriMahdi2001/Raffle-Doc/blob/master/Images/winner.png?raw=true" alt="enter image description here"></p>
<h2 id="more-technical-details">More Technical Details</h2>
<p>We presented the soal of the implemented idea in previous sections, but there are more technical details that we paid attention to them in implementation. We will review some of these technical details in this part.</p>
<p>1- <strong>No secret key needed</strong></p>
<p>As we mentioned in previous parts, the implementation of dApp is independent of the participant secret key. Since providing the secret key requires a high trust in service, it is not a desirable requirement in a service like ours; thus we implemented our service that is completely independent of the user secret key. To do so we use <em>pay to scripts</em> and implement tickets in a way that anyone can spend them if the output belongs to the right person.</p>
<p>2- <strong>Raffle Service Token</strong></p>
<p>We considered a special token as the <em>raffle service token</em> that identifies the valid raffles which are using our service. This type of token issued in the service initialization, and all of them are under the control of our service. Each user that wants to start a new raffle sends a request to the service, if his inputs were acceptable we create a raffle box with his specified setting and one <em>raffle service token</em> which validates this raffle. Finally, after the deadline of the raffle and introducing winner or refunding participants, we take back the <em>raffle service token</em> and officially end that raffle.</p>
<p>This token can also be used for finding valid active raffles. Anyone who knows the <em>raffle service token id</em> can request its unspent boxes from the explorer and list all active raffles. We also can use this feature in our dApp too and remove the need for a centralized DataBase.</p>

<h2 id="SC">Screen Shots</h2>
<h3 id="SC">Active Raffles</h3>
<p><img src="https://github.com/NazeriMahdi2001/Raffle-Doc/blob/master/Images/SC_Active_raffles.png?raw=true" alt="enter image description here"></p>
<h3 id="SC">Raffle Participation</h3>
<p><img src="https://github.com/NazeriMahdi2001/Raffle-Doc/blob/master/Images/SC_Participation.png?raw=true" alt="enter image description here"></p>
<h3 id="SC">Create New Raffle</h3>
<p><img src="https://github.com/NazeriMahdi2001/Raffle-Doc/blob/master/Images/SC_create_raffle.png?raw=true" alt="enter image description here"></p>

