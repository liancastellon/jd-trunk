package org.jdownloader.captcha.v2.challenge.keycaptcha.jac;

import java.util.ArrayList;

import jd.controlling.captcha.SkipException;

import org.jdownloader.captcha.v2.Challenge;
import org.jdownloader.captcha.v2.ChallengeSolver;
import org.jdownloader.captcha.v2.challenge.keycaptcha.KeyCaptchaPuzzleChallenge;
import org.jdownloader.captcha.v2.challenge.keycaptcha.KeyCaptchaResponse;
import org.jdownloader.captcha.v2.solver.jac.JACSolver;
import org.jdownloader.captcha.v2.solver.jac.SolverException;
import org.jdownloader.captcha.v2.solverjob.SolverJob;

public class KeyCaptchaJACSolver extends ChallengeSolver<String> {
    private static final KeyCaptchaJACSolver INSTANCE = new KeyCaptchaJACSolver();

    public static KeyCaptchaJACSolver getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean canHandle(Challenge<?> c) {
        return c != null && (c instanceof KeyCaptchaPuzzleChallenge) && !((KeyCaptchaPuzzleChallenge) c).isNoAutoSolver();
    }

    private KeyCaptchaJACSolver() {
        super(JACSolver.getInstance().getService(), 1);
    }

    @Override
    public void solve(SolverJob<String> solverJob) throws InterruptedException, SolverException, SkipException {
        try {
            final KeyCaptchaPuzzleChallenge challenge = ((KeyCaptchaPuzzleChallenge) solverJob.getChallenge());
            final KeyCaptchaAutoSolver kcSolver = new KeyCaptchaAutoSolver();
            final String out = kcSolver.solve(challenge.getHelper().getPuzzleData().getImages());
            final ArrayList<Integer> marray = new ArrayList<Integer>();
            marray.addAll(kcSolver.getMouseArray());
            if (out == null) {
                return;
            }
            final String token = challenge.getHelper().sendPuzzleResult(marray, out);
            if (token != null) {
                solverJob.addAnswer(new KeyCaptchaResponse(challenge, this, token, 95));
            }
        } catch (Throwable e) {
            solverJob.getLogger().log(e);
        }
    }
}
