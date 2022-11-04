package HAL;
import robocode.*;
import robocode.Robot;
import robocode.util.Utils;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class HAL extends AdvancedRobot
{
    private EnemyBot enemy = new EnemyBot();
    private byte radarDirection = 1;
    private int tooCloseToWall = 0;
    private int wallMargin = 60;

    static double direction = 1.0;

    private List<FuturePlace> _futurePlaces = new ArrayList<FuturePlace>();

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
                //setMaxVelocity(0); // stop!!!
            }
        }
    }

    /**
     * onHitWall: What to do when you hit a wall
     */
    public void onHitWall(HitWallEvent e) {
        direction = -1 * direction;
        out.println("Hit wall");
    }

    public void onHitRobot(HitRobotEvent e) {
        tooCloseToWall = 0;
        direction = -1 * direction;
        out.println("Hit robot");
    }

    public void adjustMovement()
    {
        // always square off our enemy, turning slightly toward him
        setTurnRight(Utils.normalRelativeAngleDegrees(enemy.getBearing() + 90 - (15 * direction)));

        // if we're close to the wall, eventually, we'll move away
        if (tooCloseToWall > 0) tooCloseToWall--;

        // switch directions if we've stopped or if enemy fired
        // (also handles moving away from the wall if too close)
        if ((getVelocity() == 0) || enemy.getHasFired()) {
            direction *= -1;
            setAhead(10000 * direction);
        }

        //if (getTime() % 20 == 0) {
        //    direction *= -1;
        //}
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
        double firePower = Math.min(400 / enemy.getDistance(),3);
        double bulletSpeed = 20 - firePower * 3;
        long time = (long)(enemy.getDistance() / bulletSpeed);
        var futurePlace = new FuturePlace(enemy.getFutureX(time), enemy.getFutureY(time), this.getTime() + time);
        double absDeg = absoluteBearing(getX(), getY(), futurePlace.getFutureX(), futurePlace.getFutureY());
        setTurnGunRight(Utils.normalRelativeAngleDegrees(absDeg - getGunHeading()));

        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
            setFire(firePower);

            this._futurePlaces.add(futurePlace);
        }
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
            if (time > futurePlace.getTime()) {
                out.println("removing");
                _futurePlaces.remove(i);
                i--;
            } else {
                g.setColor(Color.blue);
                g.fillRect(
                        (int)Math.round(futurePlace.getFutureX())-18,
                        (int)Math.round(enemy.getY())-18,
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

    public FuturePlace(double futureX, double futureY, long time)
    {

        this.futureX = futureX;
        this.futureY = futureY;
        this.time = time;
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

    public EnemyBot()
    {
        reset();
    }

    public void update(ScannedRobotEvent e, Robot robot)
    {
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
}