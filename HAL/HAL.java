package HAL;
import robocode.*;
import robocode.Robot;
import robocode.util.Utils;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.List;

public class HAL extends AdvancedRobot
{
    private EnemyBot enemy = new EnemyBot();
    private FireSelection fireSelection = new FireSelection();
    private byte radarDirection = 1;
    private int tooCloseToWall = 0;
    private long lastDirectionChange = -9999;
    private int directionChangeMargin = 20;
    private int wallMargin = 100;

    static double direction = 1.0;

    private List<FuturePlace> _futurePlaces = new ArrayList<FuturePlace>();

    static HashMap<Bullet, Integer> bulletList = new HashMap<Bullet, Integer>();
    static HashMap<Integer, Integer> bulletHits = new HashMap<Integer, Integer>();
    static HashMap<Integer, Integer> bulletFired = new HashMap<Integer, Integer>();
    static Random randomizer = new Random();
    int bulletCount = 0;

    public void run() {

        // Initialization of the robot should be put here
        setAdjustRadarForGunTurn(true);
        setAdjustGunForRobotTurn(true);

        addCustomEvent(new Condition("too_close_to_walls") {
            public boolean test() {
                return (
                        // we're too close to the left wall
                        (getX() <= wallMargin ||
                                // or we're too close to the right wall
                                getX() >= getBattleFieldWidth() - wallMargin ||
                                // or we're too close to the bottom wall
                                getY() <= wallMargin ||
                                // or we're too close to the top wall
                                getY() >= getBattleFieldHeight() - wallMargin)
                );
            }
        });

        // Robot main loop
        while(true) {
            adjustRadar();
            adjustMovement();
            setFire();
            //setFireWeightedRandom();
            execute();
        }
    }

    /**
     * onScannedRobot: What to do when you see another robot
     */
    public void onScannedRobot(ScannedRobotEvent e) {
        if ( enemy.none() || e.getDistance() < enemy.getDistance() - 120 || e.getName().equals(enemy.getName())) {
            enemy.update(e, this);
        }
    }

    public void onRobotDeath(RobotDeathEvent e) {
        // see if the robot we were tracking died
        if (e.getName().equals(enemy.getName())) {
            enemy.reset();
        }
    }


    public void onHitByBullet(HitByBulletEvent e) {
        //direction = -1 * direction;
    }

    public void onCustomEvent(CustomEvent e) {
        if (e.getCondition().getName().equals("too_close_to_walls"))
        {
            if (tooCloseToWall <= 0) {
                tooCloseToWall += wallMargin;
                direction = -1 * direction;
                out.println("change dir too close");
            }
        }
    }

    /**
     * onHitWall: What to do when you hit a wall
     */
    public void onHitWall(HitWallEvent e) {
       // direction = -1 * direction;
        out.println("Hit wall");
    }

    public void onHitRobot(HitRobotEvent e) {
        tooCloseToWall = 0;
        direction = -1 * direction;
        out.println("Hit robot");
    }

    public void onBulletHit(BulletHitEvent e) {
        Bullet bullet = e.getBullet();

        if (bulletList.containsKey(bullet)) {
            Integer delta = bulletList.get(bullet);
            out.println("Hit with delta " + delta);
            if (!bulletHits.containsKey(delta)) {
                bulletHits.put(delta, 1);
            }
            else {
                Integer hit = bulletHits.get(delta);
                bulletHits.replace(delta, hit+1);
            }
            bulletList.remove(bullet);
        }
    }

    public void onBulletHitBullet(BulletHitBulletEvent e) {
        Bullet bullet = e.getBullet();

        if (bulletList.containsKey(bullet)) {
            bulletList.remove(bullet);
        }
    }

    public void onBulletMissed(BulletMissedEvent e) {
        Bullet bullet = e.getBullet();

        if (bulletList.containsKey(bullet)) {
            Integer delta = bulletList.get(bullet);
            out.println("Miss with delta " + delta);

            bulletList.remove(bullet);
         }
    }

    public void onRoundEnded(RoundEndedEvent e) {
        for (Map.Entry<Integer, Integer> set : bulletFired.entrySet()) {
            int hits = bulletHits.get(set.getKey());
            int fired = set.getValue();
            out.println("For delta " + set.getKey() + " : " + (double)hits/(double)fired*100 + " (" + hits + "/" + fired + ")");
        }
    }

    public void adjustMovement()
    {
        // always square off our enemy, turning slightly toward him
        setTurnRight(Utils.normalRelativeAngleDegrees(enemy.getBearing() + 90 - (15 * direction)));
        double xDistanceToWall =  getBattleFieldWidth()-getX();
        double yDistanceToWall = getBattleFieldHeight()-getY();

        boolean tooClose = (xDistanceToWall<=wallMargin || yDistanceToWall<=wallMargin || getX()<=wallMargin || getY()<=wallMargin);
        
        // if we're close to the wall, eventually, we'll move away
        if (tooCloseToWall > 0) tooCloseToWall--;

        // switch directions if we've stopped or if enemy fired
        // (also handles moving away from the wall if too close)
        if ((getVelocity() == 0) || (enemy.getHasFired() && !tooClose)) {
            if (changeDirection()) {
                setAhead(10000 * direction);
            }
        }
    }

    private boolean changeDirection() {
        if ((getTime() - lastDirectionChange) > directionChangeMargin) {
            direction *= -1;
            lastDirectionChange = getTime();
            return true;
        }
        return false;
    }

    public void adjustRadar()
    {
        if(enemy.none()){
            setTurnRadarRight(360);
            return;
        }

        /*if ( getRadarTurnRemaining() == 0.0 ) {
            setTurnRadarRightRadians( Double.POSITIVE_INFINITY );
        }*/

        double angleToEnemy = getHeading() + enemy.getBearing();

        // Subtract current radar heading to get the turn required to face the enemy, be sure it is normalized
        double radarTurn = Utils.normalRelativeAngleDegrees( angleToEnemy - getRadarHeading() );

        // Distance we want to scan from middle of enemy to either side
        // The 36.0 is how many units from the center of the enemy robot it scans.
        double extraTurn = Math.min( Math.toDegrees(Math.atan( 36.0 / enemy.getDistance() )), Rules.RADAR_TURN_RATE );

        // Adjust the radar turn so it goes that much further in the direction it is going to turn
        // Basically if we were going to turn it left, turn it even more left, if right, turn more right.
        // This allows us to overshoot our enemy so that we get a good sweep that will not slip.
        if (radarTurn < 0)
            radarTurn -= extraTurn;
        else
            radarTurn += extraTurn;

        //Turn the radar
        setTurnRadarRight(radarTurn);
    }

    public void setFire()
    {
        if (enemy.none()) {
            return;
        }

        var futurePlace = fireSelection.getNextFire(enemy,this);
        double absDeg = absoluteBearing(getX(), getY(), futurePlace.getFutureX(), futurePlace.getFutureY());
        setTurnGunRight(Utils.normalRelativeAngleDegrees(absDeg - getGunHeading()));

        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
            setFire(futurePlace.getFirePower());
            this._futurePlaces.add(futurePlace);
        }
    }

    public void setFireWeightedRandom()
    {
        if (enemy.none()) {
            return;
        }
        double firePower = Math.min(400 / enemy.getDistance(),3);
        double bulletSpeed = 20 - firePower * 3;
        long time = (long)(enemy.getDistance() / bulletSpeed);
        double futureX = enemy.getFutureX(time);
        double futureY = enemy.getFutureY(time);
        double absDeg = absoluteBearing(getX(), getY(), futureX, futureY);
        int delta = getDelta();
        setTurnGunRight(Utils.normalRelativeAngleDegrees(absDeg + delta - getGunHeading()));

        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
            bulletList.put(setFireBullet(firePower), delta);
            if (!bulletFired.containsKey(delta)) {
                bulletFired.put(delta, 1);
                bulletHits.put(delta, 0);
            }
            else {
                bulletFired.replace(delta, bulletFired.get(delta)+1);
            }
            bulletCount++;
        }
    }
    
    private int getDelta() {
        if (((getNumRounds() == 0) && (bulletCount < 10)) || (getRoundNum() == 0)) {
            return (randomizer.nextInt(10)-5) * 5;
        }
        
        ArrayList<Integer> bestDeltas = new ArrayList<Integer>();
        ArrayList<Double> bestValues = new ArrayList<Double>();
        int numDeltas = 4;
        for (Map.Entry<Integer, Integer> set : bulletFired.entrySet()) {
            double value = (double)bulletHits.get(set.getKey()) / (double)set.getValue();
            if (bestDeltas.size() < numDeltas) {
                bestDeltas.add(set.getKey());
                bestValues.add(value);
            }
            else {
                boolean isGreater = true;
                for (int i = 0; (i < numDeltas) && isGreater; i++) {
                    if (bestValues.get(i) > value) {
                        isGreater = false;
                    }
                }
                if (isGreater) {
                    int minIndex = 0;
                    double minValue = bestValues.get(minIndex);
                    for (int i = 1; i < numDeltas; i++) {
                        if (bestValues.get(i) < minValue) {
                            minIndex = i;
                            minValue = bestValues.get(i);
                        }
                    }
                    bestValues.set(minIndex, value);
                    bestDeltas.set(minIndex, set.getKey());
                }
            }
        }
        int bestIndex = randomizer.nextInt(numDeltas);
        Integer bestDelta = bestDeltas.get(bestIndex);
        double bestValue = bestValues.get(bestIndex);
        out.println("Best delta is " + bestDelta + " with value " + bestValue);     

        return bestDelta;
    }

    double absoluteBearing(double x1, double y1, double x2, double y2) {
        double xo = x2-x1;
        double yo = y2-y1;
        double hyp = Point2D.distance(x1, y1, x2, y2);
        double arcSin = Math.toDegrees(Math.asin(xo / hyp));
        double bearing = 0;

        if (xo > 0 && yo > 0) { // both pos: lower-Left
            bearing = arcSin;
        } else if (xo < 0 && yo > 0) { // x neg, y pos: lower-right
            bearing = 360 + arcSin; // arcsin is negative here, actuall 360 - ang
        } else if (xo > 0 && yo < 0) { // x pos, y neg: upper-left
            bearing = 180 - arcSin;
        } else if (xo < 0 && yo < 0) { // both neg: upper-right
            bearing = 180 - arcSin; // arcsin is negative here, actually 180 + ang
        }

        return bearing;
    }

    @Override
    public void onPaint(Graphics2D g) {

        if (!enemy.none())
        {
            g.setColor(Color.red);
            g.fillRect((int)Math.round(enemy.getX())-18, (int)Math.round(enemy.getY())-18, 36, 36);
        }

        var time= this.getTime();
        out.println("We have " + _futurePlaces.size() + " at " + time);

        for (int i=0; i < _futurePlaces.size(); i++) {
            var futurePlace = _futurePlaces.get(i);
            out.println("Shit " + futurePlace.getFutureX() + " " + futurePlace.getFutureY() + " " + futurePlace.getTime());
            if (time > futurePlace.getTime() + 15) {
                out.println("removing");
                _futurePlaces.remove(i);
                i--;
            } else {
                g.setColor(Color.blue);
                g.fillRect(
                        (int)Math.round(futurePlace.getFutureX())-18,
                        (int)Math.round(futurePlace.getFutureY())-18,
                        36, 36);
            }
        }
    }
}

class FuturePlace
{
    private double futureX;
    private double futureY;
    private long time;
    private double firePower;

    public FuturePlace(double futureX, double futureY, long time, double firePower)
    {

        this.futureX = futureX;
        this.futureY = futureY;
        this.time = time;
        this.firePower = firePower;
    }

    public double getFutureX() {
        return futureX;
    }

    public double getFutureY() {
        return futureY;
    }

    public long getTime() {
        return time;
    }

    public double getFirePower() {
        return firePower;
    }
}

class EnemyBot
{

    private double x;
    private double y;
    private double bearing;
    private double distance;
    private double energy;
    private double heading;
    private double velocity;
    private String name;
    private boolean hasFired;

    private double oldEnemyHeading;

    public EnemyBot()
    {
        reset();
    }

    public void update(ScannedRobotEvent e, Robot robot)
    {
        oldEnemyHeading = heading;
        hasFired = e.getEnergy() < energy;
        bearing = e.getBearing();
        distance = e.getDistance();
        energy = e.getEnergy();
        heading = e.getHeading();
        velocity = e.getVelocity();
        name = e.getName();

        double absBearingDeg = (robot.getHeading() + e.getBearing());
        if (absBearingDeg < 0) absBearingDeg += 360;

        x = robot.getX() + Math.sin(Math.toRadians(absBearingDeg)) * e.getDistance();
        y = robot.getY() + Math.cos(Math.toRadians(absBearingDeg)) * e.getDistance();
    }

    public void reset()
    {
        x = 0;
        y = 0;
        bearing = 0;
        distance = 0;
        energy = 100;
        heading = 0;
        velocity = 0;
        name = "";
        hasFired = false;
    }

    public double getX()
    {
        return x;
    }

    public double getY()
    {
        return y;
    }

    public double getBearing()
    {
        return bearing;
    }

    public double getDistance()
    {
        return distance;
    }

    public double getEnergy()
    {
        return energy;
    }

    public double getHeading()
    {
        return heading;
    }

    public double getVelocity()
    {
        return velocity;
    }

    public double getFutureX(long time)
    {
        return x + Math.sin(Math.toRadians(getHeading())) * getVelocity() * time;
    }

    public double getFutureY(long time)
    {
        return y + Math.cos(Math.toRadians(getHeading())) * getVelocity() * time;
    }

    public String getName()
    {
        return name;
    }

    public boolean getHasFired()
    {
        return hasFired;
    }

    public boolean none()
    {
        return name.isEmpty();
    }

    public double getOldEnemyHeading() {
        return oldEnemyHeading;
    }
}


class FireSelection
{
    private final int index;
    private ArrayList<FireStrategy> strategies;

    public FireSelection()
    {
        this.strategies = new ArrayList<FireStrategy>();
        this.strategies.add(new BaseStrategy());
        this.strategies.add(new CircularStrategy());
        this.index = 1;
    }

    FuturePlace getNextFire(EnemyBot enemyBot, AdvancedRobot robot)
    {
        return this.strategies.get(index).getNextFire(enemyBot, robot);
    }
}


interface FireStrategy
{
    String getId();

    FuturePlace getNextFire(EnemyBot enemyBot, AdvancedRobot robot);
}

class BaseStrategy implements FireStrategy
{

    @Override
    public String getId() {
        return "base";
    }

    @Override
    public FuturePlace getNextFire(EnemyBot enemyBot, AdvancedRobot robot) {
        if (enemyBot.none())
            return null;
        double firePower = Math.min(400 / enemyBot.getDistance(),3);
        double bulletSpeed = 20 - firePower * 3;
        long time = (long)(enemyBot.getDistance() / bulletSpeed);
        var futurePlace = new FuturePlace(
                enemyBot.getFutureX(time), enemyBot.getFutureY(time), robot.getTime() + time, firePower);
        return futurePlace;
    }
}

class CircularStrategy implements FireStrategy
{
    @Override
    public String getId() {
        return "circular";
    }

    @Override
    public FuturePlace getNextFire(EnemyBot enemyBot, AdvancedRobot robot) {
        double firePower = Math.min(400 / enemyBot.getDistance(), 3);
        double myX = robot.getX();
        double myY = robot.getY();
        double enemyX = enemyBot.getX();
        double enemyY = enemyBot.getY();
        double enemyHeadingRad = Math.toRadians(enemyBot.getHeading());
        double enemyHeadingChangeRad = enemyHeadingRad - Math.toRadians(enemyBot.getOldEnemyHeading());
        double enemyVelocity = enemyBot.getVelocity();


        long deltaTime = 0;
        double battleFieldHeight = robot.getBattleFieldHeight(),
                battleFieldWidth = robot.getBattleFieldWidth();
        double predictedX = enemyX, predictedY = enemyY;
        while ((++deltaTime) * (20.0 - 3.0 * firePower) < Point2D.Double.distance(myX, myY, predictedX, predictedY)) {
            predictedX += Math.sin(enemyHeadingRad) * enemyVelocity;
            predictedY += Math.cos(enemyHeadingRad) * enemyVelocity;
            enemyHeadingRad += enemyHeadingChangeRad;
            if (predictedX < 18.0
                    || predictedY < 18.0
                    || predictedX > battleFieldWidth - 18.0
                    || predictedY > battleFieldHeight - 18.0) {

                predictedX = Math.min(Math.max(18.0, predictedX), battleFieldWidth - 18.0);
                predictedY = Math.min(Math.max(18.0, predictedY), battleFieldHeight - 18.0);
                break;
            }
        }

        return new FuturePlace(predictedX, predictedY, robot.getTime() + deltaTime, firePower);

    }
}
